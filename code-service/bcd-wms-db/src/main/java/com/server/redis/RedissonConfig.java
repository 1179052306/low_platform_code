package com.server.redis;

/**
 * @author lw
 * @date: 2026/4/21
 * @description:
 **/
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnClass(RedissonClient.class)
public class RedissonConfig {

    // --- 基础连接信息 ---
    @Value("${spring.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.redis.port:6379}")
    private int port;

    @Value("${spring.redis.password:}")
    private String password;

    @Value("${spring.redis.database:0}")
    private int database;

    @Value("${spring.redis.timeout:3000ms}")
    private String timeoutStr; // 支持 "3000ms" 或 "3s"

    // --- 连接池配置 (生产环境核心) ---
    @Value("${spring.redis.lettuce.pool.max-active:16}")
    private int poolMaxActive; // 最大连接数

    @Value("${spring.redis.lettuce.pool.max-idle:8}")
    private int poolMaxIdle;   // 最大空闲连接

    @Value("${spring.redis.lettuce.pool.min-idle:4}")
    private int poolMinIdle;   // 最小空闲连接

    // --- 高级配置 ---
    @Value("${spring.redis.mode:single}") // single, sentinel, cluster
    private String mode;

    @Value("${spring.redis.sentinel.nodes:}") // 哨兵节点列表
    private String sentinelNodes;

    @Value("${spring.redis.sentinel.master:}") // 主节点名称
    private String sentinelMaster;

    @Value("${spring.redis.cluster.nodes:}") // 集群节点列表
    private String clusterNodes;

    /**
     * 解析超时时间（毫秒）
     */
    private int parseTimeout(String timeoutStr) {
        if (timeoutStr.contains("ms")) {
            return Integer.parseInt(timeoutStr.replace("ms", "").trim());
        } else if (timeoutStr.contains("s")) {
            return Integer.parseInt(timeoutStr.replace("s", "").trim()) * 1000;
        }
        return 3000; // 默认 3000ms
    }

    /**
     * 创建 RedissonClient
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient() {
        Config config = new Config();
        Codec codec = new JsonJacksonCodec(); // 生产建议：如果需要更高性能，可替换为 Kryo 或 FST
        config.setCodec(codec);
        config.setThreads(Runtime.getRuntime().availableProcessors() * 2); // 线程数优化
        config.setNettyThreads(Runtime.getRuntime().availableProcessors() * 2);

        int timeout = parseTimeout(timeoutStr);

        // 根据配置模式构建不同的服务器配置
        if ("cluster".equalsIgnoreCase(mode)) {
            buildClusterConfig(config, timeout);
        } else if ("sentinel".equalsIgnoreCase(mode)) {
            buildSentinelConfig(config, timeout);
        } else {
            buildSingleConfig(config, timeout);
        }

        return Redisson.create(config);
    }

    /**
     * 单机模式配置
     */
    private void buildSingleConfig(Config config, int timeout) {
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress(String.format("redis://%s:%d", host, port))
                .setDatabase(database)
                .setTimeout(timeout)
                .setConnectTimeout(3000) // 连接超时
                .setRetryAttempts(3)     // 失败重试次数
                .setRetryInterval(1500); // 重试间隔

        if (StringUtils.hasText(password)) {
            serverConfig.setPassword(password);
        }

        // 连接池配置
        serverConfig.setConnectionMinimumIdleSize(poolMinIdle)
                .setConnectionPoolSize(poolMaxActive)
                .setIdleConnectionTimeout(10000);
    }

    /**
     * 哨兵模式配置
     */
    private void buildSentinelConfig(Config config, int timeout) {
        if (!StringUtils.hasText(sentinelNodes) || !StringUtils.hasText(sentinelMaster)) {
            throw new IllegalArgumentException("哨兵模式必须配置 spring.redis.sentinel.nodes 和 spring.redis.sentinel.master");
        }

        List<String> nodes = Arrays.stream(sentinelNodes.split(","))
                .map(String::trim)
                .collect(Collectors.toList());

        SentinelServersConfig serverConfig = config.useSentinelServers()
                .addSentinelAddress(nodes.toArray(new String[0]))
                .setMasterName(sentinelMaster)
                .setTimeout(timeout)
                .setConnectTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        if (StringUtils.hasText(password)) {
            serverConfig.setPassword(password);
        }

        serverConfig.setMasterConnectionMinimumIdleSize(poolMinIdle)
                .setMasterConnectionPoolSize(poolMaxActive)
                .setSlaveConnectionMinimumIdleSize(poolMinIdle)
                .setSlaveConnectionPoolSize(poolMaxActive);
    }

    /**
     * 集群模式配置
     */
    private void buildClusterConfig(Config config, int timeout) {
        if (!StringUtils.hasText(clusterNodes)) {
            throw new IllegalArgumentException("集群模式必须配置 spring.redis.cluster.nodes");
        }

        List<String> nodes = Arrays.stream(clusterNodes.split(","))
                .map(String::trim)
                .collect(Collectors.toList());

        ClusterServersConfig serverConfig = config.useClusterServers()
                .addNodeAddress(nodes.toArray(new String[0]))
                .setTimeout(timeout)
                .setConnectTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500)
                .setScanInterval(2000); // 集群拓扑扫描间隔

        if (StringUtils.hasText(password)) {
            serverConfig.setPassword(password);
        }

        serverConfig.setMasterConnectionMinimumIdleSize(poolMinIdle)
                .setMasterConnectionPoolSize(poolMaxActive)
                .setSlaveConnectionMinimumIdleSize(poolMinIdle)
                .setSlaveConnectionPoolSize(poolMaxActive);
    }
}
