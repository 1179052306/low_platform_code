package com.api.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;

/** 登录失败计数：auth:fail:{username}:{ip}，>=5 锁 10 分钟（组合维度防 victim lockout） */
@Service
public class LoginGuard {

    private static final int MAX_FAIL = 5;

    @Value("${auth.fail.seconds:600}")
    private long failSeconds;

    @Resource
    private StringRedisTemplate redis;

    public boolean isLocked(String username, String ip) {
        String key = key(username, ip);
        String v = redis.opsForValue().get(key);
        return v != null && Integer.parseInt(v) >= MAX_FAIL;
    }

    public boolean recordFail(String username, String ip) {
        String key = key(username, ip);
        Long v = redis.opsForValue().increment(key);
        redis.expire(key, Duration.ofSeconds(failSeconds));
        return v != null && v >= MAX_FAIL;
    }

    public void clear(String username, String ip) {
        redis.delete(key(username, ip));
    }

    private String key(String username, String ip) {
        return "auth:fail:" + (username == null ? "" : username.toLowerCase()) + ":" + (ip == null ? "unknown" : ip);
    }
}