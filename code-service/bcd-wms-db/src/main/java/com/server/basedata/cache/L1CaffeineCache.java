package com.server.basedata.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;

/**
 * L1 本地缓存（基于 Caffeine）。
 * <p>
 * 作为两级缓存的第一级，承载进程内热点数据，提供低延迟读取。通过 maximumSize 控制容量上限、
 * expireAfterWrite 控制写入后过期时间，并开启 recordStats 供指标采集。
 * <p>
 * 写入时结合 {@link CacheAdmission} 做准入过滤，仅放行访问频次达到阈值的 key，
 * 避免一次性大量 key 污染缓存。
 */
public class L1CaffeineCache {

  /** Caffeine 缓存实例 */
  private final Cache<String, Object> cache;
  /** 缓存准入策略，可为 null（表示全部准入） */
  private final CacheAdmission admission;

  /**
   * 构造 L1 缓存。
   *
   * @param maxSize   最大条目数
   * @param ttlMs     写入后过期时间（毫秒）
   * @param admission 缓存准入策略，null 表示不做准入过滤
   */
  public L1CaffeineCache(long maxSize, long ttlMs, CacheAdmission admission) {
    this.cache = Caffeine.newBuilder()
        .maximumSize(maxSize)
        .expireAfterWrite(ttlMs, TimeUnit.MILLISECONDS)
        .recordStats()
        .build();
    this.admission = admission;
  }

  /**
   * 按 key 获取缓存值。
   *
   * @param key 缓存 key
   * @return 缓存值；不存在时返回 null
   */
  public Object get(String key) {
    return cache.getIfPresent(key);
  }

  /**
   * 写入缓存条目。
   * <p>
   * 当存在准入策略时，仅当 key 通过准入判定才写入；否则直接写入。
   *
   * @param key   缓存 key
   * @param value 缓存值
   */
  public void put(String key, Object value) {
    boolean shouldAdmit;
    if (admission == null) {
      // 无准入策略，全部放行
      shouldAdmit = true;
    } else {
      shouldAdmit = admission.shouldAdmit(key);
    }
    if (shouldAdmit) {
      cache.put(key, value);
    }
  }

  /**
   * 失效指定 key。
   *
   * @param key 缓存 key
   */
  public void invalidate(String key) {
    cache.invalidate(key);
  }

  /** 失效全部条目 */
  public void invalidateAll() {
    cache.invalidateAll();
  }

  /**
   * 获取缓存估算大小。
   * <p>
   * Caffeine 返回的是近似值，可能因过期清理滞后而略大于实际有效条目数。
   *
   * @return 缓存估算大小
   */
  public long size() {
    return cache.estimatedSize();
  }

  /**
   * 获取准入策略。
   *
   * @return 准入策略，可能为 null
   */
  public CacheAdmission getAdmission() {
    return admission;
  }
}
