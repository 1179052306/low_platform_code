package com.server.basedata.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 缓存准入策略。
 * <p>
 * 基于访问频次实现轻量级准入过滤：仅当某 key 的累计访问次数达到阈值时才允许进入 L1 缓存，
 * 避免一次性大量低频 key 污染缓存（类似 TinyLFU 的思想，但实现更简单）。
 * <p>
 * 计数使用 {@link ConcurrentHashMap} + {@link AtomicInteger}，保证线程安全。
 */
public class CacheAdmission {

  /** 准入阈值：访问次数达到该值才允许入缓存 */
  private final int threshold;
  /** 各 key 的访问计数器 */
  private final ConcurrentHashMap<String, AtomicInteger> accessCounter;

  /**
   * 构造准入策略。
   *
   * @param threshold 准入阈值
   */
  public CacheAdmission(int threshold) {
    this.threshold = threshold;
    this.accessCounter = new ConcurrentHashMap<String, AtomicInteger>();
  }

  /**
   * 判断指定 key 是否应被准入缓存。
   * <p>
   * 当累计访问次数 ≥ 阈值时返回 true。
   *
   * @param key 缓存 key
   * @return 应准入返回 true，否则返回 false
   */
  public boolean shouldAdmit(String key) {
    AtomicInteger counter = accessCounter.get(key);
    int count;
    if (counter == null) {
      // 未记录过访问，计为 0
      count = 0;
    } else {
      count = counter.get();
    }
    return count >= threshold;
  }

  /**
   * 记录一次对指定 key 的访问。
   * <p>
   * 使用 putIfAbsent 保证并发场景下计数器只创建一次，随后原子自增。
   *
   * @param key 缓存 key
   */
  public void recordAccess(String key) {
    AtomicInteger counter = accessCounter.get(key);
    if (counter == null) {
      // 并发创建计数器，确保只保留一个
      AtomicInteger newCounter = new AtomicInteger(0);
      AtomicInteger existing = accessCounter.putIfAbsent(key, newCounter);
      if (existing == null) {
        counter = newCounter;
      } else {
        counter = existing;
      }
    }
    counter.incrementAndGet();
  }

  /**
   * 重置指定 key 的访问计数。
   * <p>
   * 通常在 key 被失效或淘汰后调用，使其重新从 0 开始累计。
   *
   * @param key 缓存 key
   */
  public void reset(String key) {
    accessCounter.remove(key);
  }

  /**
   * 获取指定 key 的当前访问计数。
   *
   * @param key 缓存 key
   * @return 访问计数；未记录过返回 0
   */
  public int getAccessCount(String key) {
    AtomicInteger counter = accessCounter.get(key);
    int result;
    if (counter == null) {
      result = 0;
    } else {
      result = counter.get();
    }
    return result;
  }
}
