package com.api.auth.service;

import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.SqlEngineJson;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.UUID;

/**
 * 认证会话管理（auth 侧，数据库操作全走 SqlEngineJson helper）：
 * - auth:user-sessions:{userId} = ZSET（member=accessToken，score=过期毫秒）供 bcd-system 踢人
 * - auth:online:{md5(accessToken)} = 会话详情 JSON
 */
@Service
public class SessionService {

    @Resource
    private TokenStore tokenStore;

    @Resource
    private StringRedisTemplate redis;

    @Resource
    private SqlEngineJson helper;

    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String now() {
        return java.time.Instant.now().toString();
    }

    public void saveOnline(String userId, String deviceId, String accessToken, long expiresAtMillis,
                           String ip, String ua, String username) {
        String zkey = "auth:user-sessions:" + userId;
        redis.opsForZSet().add(zkey, accessToken, expiresAtMillis);
        redis.expireAt(zkey, new java.util.Date(expiresAtMillis + 300_000L));
        JSONObject detail = new JSONObject();
        detail.put("uid", userId);
        detail.put("username", username);
        detail.put("deviceId", deviceId == null || deviceId.isEmpty() ? "web-default" : deviceId);
        detail.put("ip", ip);
        detail.put("ua", ua);
        detail.put("loginAt", now());
        detail.put("expireAt", expiresAtMillis);
        redis.opsForValue().set(detailKey(accessToken), detail.toJSONString(),
                java.time.Duration.ofMillis(Math.max(1000, expiresAtMillis - System.currentTimeMillis())));
    }

    /** 登出/同设备重登顶替：删 access+refresh token + 清索引 */
    public void evictSession(String accessToken) {
        if (accessToken == null) {
            return;
        }
        String detail = redis.opsForValue().get(detailKey(accessToken));
        String userId = null;
        if (detail != null) {
            try {
                userId = JSONObject.parseObject(detail).getString("uid");
            } catch (Exception ignore) {
            }
        }
        removeToken(accessToken);
        redis.delete(detailKey(accessToken));
        if (userId != null) {
            redis.opsForZSet().remove("auth:user-sessions:" + userId, accessToken);
        }
    }

    public void updateLastLogin(String userId) {
        try {
            com.alibaba.fastjson2.JSONObject data = new com.alibaba.fastjson2.JSONObject();
            data.put("last_login_at", now());
            SqlEngineJson.check(helper.update("lc_user", data, helper.eq("id", userId)));
        } catch (Exception ignore) {
        }
    }

    public void writeLoginLog(String userId, String username, String deviceId, String ip,
                              boolean success, String message) {
        try {
            com.alibaba.fastjson2.JSONObject data = new com.alibaba.fastjson2.JSONObject();
            data.put("id", uuid());
            data.put("user_id", userId);
            data.put("username", username);
            data.put("login_type", "password");
            data.put("device_id", deviceId);
            data.put("success", success);
            data.put("message", message);
            data.put("ip", ip);
            data.put("login_at", now());
            SqlEngineJson.check(helper.insert("lc_login_log", data));
        } catch (Exception ignore) {
        }
    }

    private void removeToken(String accessToken) {
        try {
            OAuth2AccessToken at = tokenStore.readAccessToken(accessToken);
            if (at != null) {
                OAuth2RefreshToken rt = at.getRefreshToken();
                if (rt != null) {
                    tokenStore.removeRefreshToken(rt);
                }
                tokenStore.removeAccessToken(at);
            }
        } catch (Exception ignore) {
        }
    }

    private String detailKey(String accessToken) {
        return "auth:online:" + md5(accessToken);
    }

    private static String md5(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return String.format("%032x", new java.math.BigInteger(1, d));
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }
}