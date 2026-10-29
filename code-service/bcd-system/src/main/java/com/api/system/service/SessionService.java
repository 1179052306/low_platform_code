package com.api.system.service;

import com.alibaba.fastjson2.JSONObject;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * OAuth2 会话管理（与 bcd-auth 共享 RedisTokenStore）：
 * - auth:user-sessions:{userId} = ZSET（member=accessToken，score=过期毫秒；操作前 ZREMRANGEBYSCORE 自愈）
 * - auth:online:{md5(accessToken)} = 会话详情 JSON
 * - 踢人 = tokenStore.removeAccessToken/removeRefreshToken（删 token 即失效，资源服务器查 store 校验）
 */
@Service
public class SessionService {

    @Resource
    private TokenStore tokenStore;

    @Resource
    private StringRedisTemplate redis;

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

    /** 踢用户全部会话（角色变更/禁用/删除即踢下线） */
    public int kickUser(String userId) {
        String zkey = "auth:user-sessions:" + userId;
        cleanExpired(zkey);
        Set<String> tokens = redis.opsForZSet().range(zkey, 0, -1);
        int n = 0;
        if (tokens != null) {
            for (String token : tokens) {
                removeToken(token);
                redis.delete(detailKey(token));
                n++;
            }
        }
        redis.delete(zkey);
        return n;
    }

    public int kickRoleUsers(List<String> userIds) {
        int n = 0;
        for (String uid : userIds) {
            n += kickUser(uid);
        }
        return n;
    }

    /** 在线会话列表（用户×设备维度） */
    public List<JSONObject> onlineSessions() {
        List<JSONObject> list = new ArrayList<>();
        Set<String> keys = redis.keys("auth:user-sessions:*");
        if (keys == null) {
            return list;
        }
        for (String zkey : keys) {
            cleanExpired(zkey);
            Set<String> tokens = redis.opsForZSet().range(zkey, 0, -1);
            if (tokens == null) {
                continue;
            }
            for (String token : tokens) {
                String v = redis.opsForValue().get(detailKey(token));
                if (v != null) {
                    try {
                        list.add(JSONObject.parseObject(v));
                    } catch (Exception ignore) {
                    }
                }
            }
        }
        list.sort((a, b) -> String.valueOf(b.getString("loginAt")).compareTo(String.valueOf(a.getString("loginAt"))));
        return list;
    }

    private void cleanExpired(String zkey) {
        redis.opsForZSet().removeRangeByScore(zkey, 0, System.currentTimeMillis());
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