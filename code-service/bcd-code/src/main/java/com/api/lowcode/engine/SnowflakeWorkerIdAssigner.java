package com.api.lowcode.engine;

import com.server.redis.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Snowflake workerId 自动分配器（多实例安全）
 *
 * 启动时从 Redis 占位获取唯一 workerId（0-1023），心跳续约保活，宕机 TTL 自动释放。
 * 支持手动配置 snowflake.worker-id 跳过自动分配（单实例或固定 ID 场景）。
 */
@Component
public class SnowflakeWorkerIdAssigner {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeWorkerIdAssigner.class);

    private static final String KEY_PREFIX = "snowflake:worker:active:";
    private static final int MAX_WORKER_ID = 1023;
    private static final int TTL_SECONDS = 30;
    private static final int RENEW_INTERVAL_SECONDS = 10;

    @Autowired(required = false)
    private RedisService redisService;

    @Value("${snowflake.worker-id:-1}")
    private long configWorkerId;

    private long assignedWorkerId = -1;
    private ScheduledExecutorService scheduler;
    private String instanceId;

    @PostConstruct
    public void init() {
        instanceId = UUID.randomUUID().toString().replace("-", "");

        if (configWorkerId >= 0 && configWorkerId <= MAX_WORKER_ID) {
            assignedWorkerId = configWorkerId;
            log.info("[Snowflake] 使用配置的 workerId={}", assignedWorkerId);
        } else if (redisService != null) {
            assignedWorkerId = assignFromRedis();
            log.info("[Snowflake] 从 Redis 自动分配 workerId={}", assignedWorkerId);
        } else {
            assignedWorkerId = ThreadLocalRandom.current().nextLong(0, MAX_WORKER_ID + 1);
            log.warn("[Snowflake] Redis 不可用，使用随机 workerId={}（多实例可能冲突）", assignedWorkerId);
        }

        SnowflakeIdGenerator.setWorkerId(assignedWorkerId);

        if (redisService != null && configWorkerId < 0) {
            startHeartbeat();
        }
    }

    @SuppressWarnings("unchecked")
    private long assignFromRedis() {
        for (long wid = 0; wid <= MAX_WORKER_ID; wid++) {
            String key = KEY_PREFIX + wid;
            try {
                Boolean success = redisService.redisTemplate.opsForValue()
                        .setIfAbsent(key, instanceId, TTL_SECONDS, TimeUnit.SECONDS);
                if (Boolean.TRUE.equals(success)) {
                    return wid;
                }
            } catch (Exception e) {
                log.warn("[Snowflake] 尝试占位 workerId={} 失败: {}", wid, e.getMessage());
            }
        }
        long fallback = ThreadLocalRandom.current().nextLong(0, MAX_WORKER_ID + 1);
        log.warn("[Snowflake] 0-{} 全被占，使用随机 workerId={}", MAX_WORKER_ID, fallback);
        return fallback;
    }

    private void startHeartbeat() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "snowflake-heartbeat");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                String key = KEY_PREFIX + assignedWorkerId;
                redisService.expire(key, TTL_SECONDS);
            } catch (Exception e) {
                log.warn("[Snowflake] 心跳续约失败: {}", e.getMessage());
            }
        }, RENEW_INTERVAL_SECONDS, RENEW_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (redisService != null && configWorkerId < 0 && assignedWorkerId >= 0) {
            try {
                redisService.redisTemplate.delete(KEY_PREFIX + assignedWorkerId);
                log.info("[Snowflake] 释放 workerId={}", assignedWorkerId);
            } catch (Exception ignored) {
            }
        }
    }
}