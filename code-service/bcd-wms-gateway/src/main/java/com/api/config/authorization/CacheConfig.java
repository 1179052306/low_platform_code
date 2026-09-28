package com.api.config.authorization;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * @author lw
 * @date: 2026/4/20
 * @description:
 **/
@Configuration
public class CacheConfig {
    @Bean
    public Cache<String, Boolean> permissionLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(20000)           // 最大存储 2万条记录，超出自动淘汰最少使用的
                .expireAfterWrite(30, TimeUnit.MINUTES) // 写入后 30分钟过期
                .recordStats()                // 开启统计（用于监控命中率）
                .build();
    }
    /**
     * 定义一个本地缓存Bean，用于缓存登录Token
     * 最大容量10000，写入后1天自动过期
     */
    @Bean(name = "loginTokenCache")
    public Cache<String, Boolean> loginTokenCache() {
        // 核心修改：在 build() 之前，确保泛型被正确推断
        // 方法1：显式调用 build 时指定泛型（推荐）
        return Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(1, TimeUnit.DAYS)
                .build(); // 这里的 build() 会根据左边的返回值自动推断，如果推断失败，请看方法2
    }
}