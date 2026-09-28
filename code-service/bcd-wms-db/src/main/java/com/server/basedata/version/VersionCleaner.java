package com.server.basedata.version;

import org.redisson.api.RBatch;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 旧版本缓存清理器。
 * <p>
 * 当 Schema 版本切换或缓存空间下线时，需清理 Redis 中残留的旧版本 key。该清理器按 pattern
 * 扫描 key 并分批管道删除，避免一次性大量删除造成 Redis 阻塞。pattern 与
 * {@link com.server.basedata.cache.KeyBuilder}
 * 的 key 格式保持一致。
 */
public class VersionCleaner {

  /** Redisson 客户端 */
  private final RedissonClient redissonClient;
  /** 扫描与删除的批大小 */
  private final int batchSize;

  /**
   * 使用默认批大小 500 构造。
   *
   * @param redissonClient Redisson 客户端
   */
  public VersionCleaner(RedissonClient redissonClient) {
    this(redissonClient, 500);
  }

  /**
   * 指定批大小构造。
   *
   * @param redissonClient Redisson 客户端
   * @param batchSize      批大小
   */
  public VersionCleaner(RedissonClient redissonClient, int batchSize) {
    this.redissonClient = redissonClient;
    this.batchSize = batchSize;
  }

  /**
   * 清理指定缓存空间下某一旧结构版本的全部 key。
   *
   * @param cacheName        缓存空间名
   * @param oldSchemaVersion 旧结构版本号
   * @return 删除的 key 数量
   */
  public int cleanByVersion(String cacheName, int oldSchemaVersion) {
    String pattern = "bd:" + cacheName + ":s" + oldSchemaVersion + ":*";
    return cleanByPattern(pattern);
  }

  /**
   * 清理指定缓存空间下全部结构版本的 key。
   *
   * @param cacheName 缓存空间名
   * @return 删除的 key 数量
   */
  public int cleanByCacheName(String cacheName) {
    String pattern = "bd:" + cacheName + ":*";
    return cleanByPattern(pattern);
  }

  /**
   * 按 pattern 扫描并分批删除 key。
   *
   * @param pattern key 匹配 pattern
   * @return 删除的 key 数量
   */
  private int cleanByPattern(String pattern) {
    int totalDeleted = 0;
    RKeys keys = redissonClient.getKeys();
    // 使用 SCAN 避免 KEYS 阻塞 Redis
    Iterable<String> scanResult = keys.getKeysByPattern(pattern, batchSize);

    List<String> batchKeys = new ArrayList<String>();
    for (String key : scanResult) {
      batchKeys.add(key);
      // 攒批后管道删除
      if (batchKeys.size() >= batchSize) {
        totalDeleted += deleteBatch(batchKeys);
        batchKeys.clear();
      }
    }
    // 处理剩余不足一批的 key
    if (!batchKeys.isEmpty()) {
      totalDeleted += deleteBatch(batchKeys);
    }
    return totalDeleted;
  }

  /**
   * 管道化批量删除 key。
   *
   * @param keys 待删除 key 列表
   * @return 删除的 key 数量
   */
  private int deleteBatch(List<String> keys) {
    RBatch batch = redissonClient.createBatch();
    for (String key : keys) {
      batch.getBucket(key).deleteAsync();
    }
    batch.execute();
    return keys.size();
  }
}
