package com.api.lowcode.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 网关可信过滤器：校验 X-Gw-Sign（HMAC-SHA256(uid|timestamp|path)，±60s 时间窗）
 * → 填充 CurrentUserContext；local-debug=true 时无签名请求按内置 admin（ADMIN）处理。
 */
@Slf4j
@Component
@Order(1)
public class GatewaySecurityFilter extends HttpFilter {

    @Value("${lowcode.security.gw-sign-secret:}")
    private String gwSecret;

    @Value("${lowcode.security.local-debug:true}")
    private boolean localDebug;

    private static final long WINDOW_SECONDS = 60;

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String uid = req.getHeader("X-User-Id");
        String uname = req.getHeader("X-User-Name");
        String roles = req.getHeader("X-Role-Codes");
        String sign = req.getHeader("X-Gw-Sign");
        String timeStr = req.getHeader("X-Gw-Time");

        boolean signed = false;
        if (sign != null && timeStr != null && gwSecret != null && !gwSecret.isEmpty()) {
            try {
                long ts = Long.parseLong(timeStr);
                if (Math.abs(System.currentTimeMillis() - ts) <= WINDOW_SECONDS * 1000) {
                    String expect = hmac(uid + "|" + ts + "|" + req.getRequestURI(), gwSecret);
                    signed = expect != null && expect.equals(sign);
                }
            } catch (NumberFormatException ignore) {
            }
        }

        if (!signed) {
            if (localDebug) {
                log.warn("local-debug 模式：无有效网关签名，按内置 admin 处理（生产必须关闭 lowcode.security.local-debug）");
                CurrentUserContext.set(CurrentUserContext.DEBUG_ADMIN_ID, CurrentUserContext.DEBUG_ADMIN_NAME, "ADMIN");
                try {
                    chain.doFilter(req, res);
                } finally {
                    CurrentUserContext.clear();
                }
                return;
            }
            res.setStatus(401);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"res\":false,\"code\":\"401\",\"msg\":\"网关签名校验失败\",\"data\":null}");
            return;
        }

        CurrentUserContext.set(uid, uname, roles);
        try {
            chain.doFilter(req, res);
        } finally {
            CurrentUserContext.clear();
        }
    }

    private String hmac(String data, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"));
            byte[] out = mac.doFinal(data.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}