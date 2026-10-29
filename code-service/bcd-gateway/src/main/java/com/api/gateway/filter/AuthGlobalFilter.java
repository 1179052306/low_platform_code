package com.api.gateway.filter;

import com.alibaba.fastjson2.JSONObject;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

/**
 * 认证过滤器（OAuth2 RS256 JWT + Token Introspection）：
 * 白名单放行 → Bearer JWT 公钥本地验签（public.key）
 * → 调 bcd-auth /oauth/check_token 内省（登出/踢人删 token 即失效；RFC 7563 标准）
 * → 注入可信头（X-User-* / X-Device-Id / X-Gw-Sign 网关 HMAC 签名，bcd-code 校验防直连伪造）。
 * 全程响应式（Netty EventLoop 禁阻塞调用）。
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Value("${auth.jwt.public-key:classpath:public.key}")
    private String publicKeyLocation;

    @Value("${auth.gw-sign.secret}")
    private String gwSecret;

    @Value("${auth.oauth.check-token-url:http://localhost:8010/oauth/check_token}")
    private String checkTokenUrl;

    @Value("${auth.oauth.client-id:webapp}")
    private String clientId;

    @Value("${auth.oauth.client-secret:webapp}")
    private String clientSecret;

    @Value("#{'${auth.whitelist}'.split(',')}")
    private List<String> whitelist;

    private PublicKey publicKey;

    private WebClient webClient;

    @PostConstruct
    public void init() throws Exception {
        String location = publicKeyLocation.startsWith("classpath:")
                ? publicKeyLocation.substring("classpath:".length()) : publicKeyLocation;
        try (InputStream is = new ClassPathResource(location).getInputStream()) {
            String pemText = new String(readAll(is), StandardCharsets.UTF_8)
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(pemText);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
            this.publicKey = cert.getPublicKey();
        }
        if (gwSecret == null || gwSecret.getBytes(StandardCharsets.UTF_8).length < 16) {
            throw new IllegalStateException("auth.gw-sign.secret 长度不足 16 字节，拒绝启动");
        }
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        this.webClient = WebClient.builder()
                .baseUrl(checkTokenUrl)
                .defaultHeader("Authorization", "Basic " + basic)
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isWhitelist(path)) {
            return chain.filter(exchange);
        }
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return reject(exchange, 401, "未登录或令牌缺失");
        }
        String token = auth.substring(7);
        final Claims claims;
        try {
            claims = Jwts.parser().setSigningKey(publicKey).parseClaimsJws(token).getBody();
        } catch (Exception e) {
            return reject(exchange, 401, "令牌无效或已过期");
        }
        String uid = String.valueOf(claims.get("uid"));
        String uname = String.valueOf(claims.get("uname"));
        String roles = String.valueOf(claims.get("roles"));
        String deviceId = String.valueOf(claims.get("deviceId"));
        if (uid == null || "null".equals(uid) || uid.isEmpty()) {
            return reject(exchange, 401, "令牌载荷不完整");
        }
        // Token 内省（fail-closed：auth 不可用一律 503；active=false（登出/踢人已删）→ 401）
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        return webClient.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .exchange()
                .flatMap(resp -> resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> {
                            boolean active = false;
                            try {
                                JSONObject jo = JSONObject.parseObject(body);
                                active = jo != null && jo.getBooleanValue("active", false);
                            } catch (Exception ignore) {
                            }
                            return active;
                        }))
                .flatMap(active -> {
                    if (!active) {
                        return reject(exchange, 401, "令牌已失效，请重新登录");
                    }
                    long ts = System.currentTimeMillis();
                    String sign = GwSigner.sign(uid, ts, path, gwSecret);
                    ServerWebExchange trusted = exchange.mutate().request(r -> {
                        r.headers(h -> {
                            h.set("X-User-Id", uid);
                            h.set("X-User-Name", uname == null ? "" : uname);
                            h.set("X-Role-Codes", roles == null ? "" : roles);
                            h.set("X-Device-Id", deviceId == null ? "" : deviceId);
                            h.set("X-Gw-Sign", sign);
                            h.set("X-Gw-Time", String.valueOf(ts));
                        });
                    }).build();
                    return chain.filter(trusted);
                })
                .onErrorResume(e -> reject(exchange, 503, "认证服务不可用（check_token 连接失败）"));
    }

    private boolean isWhitelist(String path) {
        for (String w : whitelist) {
            String t = w.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.endsWith("/**") && path.startsWith(t.substring(0, t.length() - 2))) {
                return true;
            }
            if (path.equals(t) || path.startsWith(t + "/") || path.startsWith(t + "?")) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> reject(ServerWebExchange exchange, int status, String msg) {
        ServerHttpResponse res = exchange.getResponse();
        res.setStatusCode(HttpStatus.valueOf(status));
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        JSONObject body = new JSONObject();
        body.put("res", false);
        body.put("code", String.valueOf(status));
        body.put("msg", msg);
        body.put("data", null);
        DataBuffer buf = res.bufferFactory().wrap(body.toJSONString().getBytes(StandardCharsets.UTF_8));
        return res.writeWith(Mono.just(buf));
    }

    private static byte[] readAll(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    @Override
    public int getOrder() {
        return 0;
    }
}