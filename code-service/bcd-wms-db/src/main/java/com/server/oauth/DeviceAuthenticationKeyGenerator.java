package com.server.oauth;

import org.springframework.security.oauth2.provider.OAuth2Authentication;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台共享 Token 认证索引 key 生成器（bcd-wms-db 提供共享包）：
 * 默认（username/client_id/scope）基础上纳入 deviceId——同一用户每设备一套 access/refresh token
 * （多端并存、登出仅清当前设备）。bcd-auth 与 bcd-system 两处 TokenStore 必须使用同一实现。
 */
public class DeviceAuthenticationKeyGenerator implements org.springframework.security.oauth2.provider.token.AuthenticationKeyGenerator {

    private static final String KEY_USERNAME = "username";
    private static final String KEY_CLIENT_ID = "client_id";
    private static final String KEY_SCOPE = "scope";
    private static final String KEY_DEVICE = "device_id";

    @Override
    public String extractKey(OAuth2Authentication authentication) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(KEY_USERNAME, authentication.isClientOnly() ? "" : authentication.getName());
        values.put(KEY_CLIENT_ID, authentication.getOAuth2Request().getClientId());
        if (authentication.getOAuth2Request().getScope() != null) {
            values.put(KEY_SCOPE, org.springframework.util.StringUtils
                    .collectionToDelimitedString(authentication.getOAuth2Request().getScope(), ","));
        }
        String deviceId = authentication.getOAuth2Request().getRequestParameters().get("deviceId");
        if (deviceId != null && !deviceId.isEmpty()) {
            values.put(KEY_DEVICE, deviceId);
        }
        return digest(values);
    }

    private String digest(Map<String, String> values) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : values.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append(';');
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(sb.toString().getBytes("UTF-8"));
            return String.format("%032x", new BigInteger(1, digest));
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new IllegalStateException("MD5 算法不可用", e);
        }
    }
}