package com.server.basedata.cache;

import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * L2 Redis 缓存（基于 Redisson）。
 * <p>
 * 作为两级缓存的第二级，承载跨进程共享的基础数据。使用 StringCodec 以纯字符串形式存储，
 * 避免二进制编解码带来的兼容性开销。提供单条/批量（pipeline）读写、删除、存在性判断等能力，
 * 批量操作通过 Redisson 的 RBatch 实现管道化以降低网络往返次数。
 */
public class L2RedisCache {

  /** Redisson 客户端 */
  private final RedissonClient redissonClient;

  /**
   * 构造 L2 缓存。
   *
   * @param redissonClient Redisson 客户端
   */
  public L2RedisCache(RedissonClient redissonClient) {
    this.redissonClient = redissonClient;
  }

  /**
   * 按 key 获取字符串值。
   *
   * @param key Redis key
   * @return 字符串值；不存在时返回 null
   */
  public String get(String key) {
    RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
    return bucket.get();
  }

  /**
   * 批量获取多个 key 的值（管道化）。
   * <p>
   * 通过 RBatch 一次性提交所有 GET 请求，减少网络往返。任一 key 读取异常时对应位置填 null，
   * 不影响其它 key 的结果。
   *
   * @param keys key 列表
   * @return 与 keys 顺序对齐的值列表；不存在的 key 或读取异常的位置为 null
   */
  public List<String> mget(List<String> keys) {
    List<String> result = new ArrayList<String>(keys.size());
    RBatch batch = redissonClient.createBatch();
    List<RFuture<String>> futures = new ArrayList<RFuture<String>>(keys.size());
    // 收集所有异步 GET 请求
    for (String key : keys) {
      RFuture<String> future = batch.<String>getBucket(key, StringCodec.INSTANCE).getAsync();
      futures.add(future);
    }
    // 一次性执行管道
    batch.execute();
    // 按顺序收集结果，异常位置填 null
    for (RFuture<String> future : futures) {
      try {
        result.add(future.get());
      } catch (Exception e) {
        result.add(null);
      }
    }
    return result;
  }

  /**
   * 写入单条字符串值并设置过期时间。
   *
   * @param key        Redis key
   * @param value      字符串值
   * @param ttlSeconds 过期时间（秒）
   */
  public void set(String key, String value, long ttlSeconds) {
    RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
    bucket.set(value, ttlSeconds, TimeUnit.SECONDS);
  }

  /**
   * 管道化批量写入多个键值并统一设置过期时间。
   *
   * @param kvMap      键值映射
   * @param ttlSeconds 过期时间（秒），对所有键统一生效
   */
  public void pipelineSet(Map<String, String> kvMap, long ttlSeconds) {
    RBatch batch = redissonClient.createBatch();
    for (Map.Entry<String, String> entry : kvMap.entrySet()) {
      batch.getBucket(entry.getKey(), StringCodec.INSTANCE)
          .setAsync(entry.getValue(), ttlSeconds, TimeUnit.SECONDS);
    }
    batch.execute();
  }

  /**
   * 删除指定 key。
   *
   * @param key Redis key
   */
  public void delete(String key) {
    RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
    bucket.delete();
  }

  /**
   * 管道化批量删除多个 key。
   *
   * @param keys 待删除的 key 列表
   */
  public void mdelete(List<String> keys) {
    RBatch batch = redissonClient.createBatch();
    for (String key : keys) {
      batch.getBucket(key, StringCodec.INSTANCE).deleteAsync();
    }
    batch.execute();
  }

  /**
   * 判断指定 key 是否存在。
   *
   * @param key Redis key
   * @return 存在返回 true，否则返回 false
   */
  public boolean exists(String key) {
    RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
    return bucket.isExists();
  }

  /**
   * 按 pattern 批量删除匹配的 key。
   * <p>
   * 使用 Redisson 的 RKeys.deleteByPattern 批量删除，适用于按缓存空间清空全部条目。
   *
   * @param pattern key 匹配 pattern（如 {@code bd:B_ITEM:*}）
   * @return 删除的 key 数量
   */
  public long deleteByPattern(String pattern) {
    return redissonClient.getKeys().deleteByPattern(pattern);
  }
}
