package com.api.config.authorization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.server.db.Tenant;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 将token信息传递到下游服务中
 */
@Configuration
@Service
public class TokenTransferFilter implements WebFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.registerModule(new Jdk8Module());
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
    }

    @Resource
    private WhitelistValidate whitelistValidate;
    @Resource
    private RequestValidate requestValidate;

    @Resource
    Tenant tenant;

    @SneakyThrows
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        URI uri = exchange.getRequest().getURI();
        if (uri.getPath().equals("/system/tenantConfig/tenantConfig")) {
            return chain.filter(exchange);
        }
        String tenantId = "";
        if (!tenant.getSingle()) {
            tenantId=exchange.getRequest().getHeaders().getFirst("tenant");
        }
        if (exchange.getRequest().getHeaders().get("authorization") == null) {
            if (uri.getPath().contains("/actuator")) {
                return chain.filter(exchange);
            }
            if (uri.getPath().contains("ws")) {
                return chain.filter(exchange);
            }
            if (uri.getPath().contains("/pdasocket")) {
                return chain.filter(exchange);
            }
            if (uri.getPath().contains("/websocket")) {
                return chain.filter(exchange);
            }

            if (whitelistValidate.redisValidate(uri.getPath())) {
                return chain.filter(exchange);
            }
        }

        //获取访问携带的token并判断是否存在或有效
        String userToken = exchange.getRequest().getHeaders().getFirst("authorization");

        if (!requestValidate.getLoginToekn(userToken, tenantId)) {
            return Mono.defer(() -> Mono.just(exchange.getResponse()))
                    .flatMap(response -> {
                        response.setStatusCode(HttpStatus.UNAUTHORIZED);
                        String body = "{\"code\":401,\"msg\":\"token不合法或过期\"}";
                        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
                        return response.writeWith(Mono.just(buffer))
                                .doOnError(error -> DataBufferUtils.release(buffer));
                    });
        }

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .cast(JwtAuthenticationToken.class)
                .flatMap(authentication -> {
                    ServerHttpRequest request = exchange.getRequest();


                    ///////此处将token 里面的相关信息转发到服务层，此处转发用户相关信息
                    request = request.mutate()
                            .header("tokenInfo", toJson(authentication.getPrincipal()))
                            .build();
                    request = request.mutate()
                            .header("user_key", authentication.getTokenAttributes().get("user_key").toString())
                            .build();
                    request = request.mutate()
                            .header("usertype", authentication.getTokenAttributes().get("usertype").toString())
                            .build();
                    String user_name = "";
                    try {
                        user_name = URLEncoder.encode(authentication.getTokenAttributes().get("user_name").toString(), "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    request = request.mutate()
                            .header("user_name", user_name)
                            .build();
                    request = request.mutate()
                            .header("jurisdiction", authentication.getTokenAttributes().get("jurisdiction").toString())
                            .build();
                    request = request.mutate()
                            .header("supplier_key", authentication.getTokenAttributes().get("supplier_key").toString())
                            .build();

                    ServerWebExchange newExchange = exchange.mutate().request(request).build();

                    return chain.filter(newExchange);
                });
    }
//
//    private Mono<Void> render(ServerWebExchange exchange) {
//        ServerHttpResponse result = exchange.getResponse();
//        result.setStatusCode(HttpStatus.OK);
//        result.getHeaders().setContentType(MediaType.TEXT_HTML);
//        return result.writeWith(createBuffer(exchange));
//    }
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
//        return ReactiveSecurityContextHolder.getContext()
//                .map(SecurityContext::getAuthentication)
//                .cast(JwtAuthenticationToken.class)
//                .flatMap(authentication -> {
//
//                    ServerHttpRequest request = exchange.getRequest();
////                    request = request.mutate()
////                            .header("tokenInfo", toJson(authentication.getPrincipal()))
////                            .build();
//                    request = request.mutate()
//                            .header("tokenInfo", toJson(authentication.getPrincipal()))
//                            .build();
//                    ServerWebExchange newExchange = exchange.mutate().request(request).build();
//
//                    return chain.filter(newExchange);
//                });
//    }

    public String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
