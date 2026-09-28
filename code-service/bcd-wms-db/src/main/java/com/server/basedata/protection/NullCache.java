package com.server.basedata.protection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 空值缓存（NullCache），用于缓存穿透防护。
 * <p>
 * 当数据库查询结果为空时，将该 key 记入空值缓存并设置较短 TTL。后续相同 key 的请求
 * 命中空值缓存即直接返回 null，避免反复回源查询不存在的数据。TTL 到期后自动失效，
 * 允许重新回源（适用于数据后续可能被写入的场景）。
 */
public class NullCache {

  /** 空值 key → 过期时间戳（毫秒） */
  private final ConcurrentHashMap<String, Long> nullKeys = new ConcurrentHashMap<String, Long>();

  /**
   * 判断指定 key 是否为空值缓存命中。
   * <p>
   * 若已过期则顺手移除并返回 false。
   *
   * @param key 缓存 key
   * @return 命中空值缓存返回 true，否则返回 false
   */
  public boolean isNull(String key) {
    Long expireTime = nullKeys.get(key);
    boolean result;
    if (expireTime == null) {
      // 未记录过
      result = false;
    } else if (System.currentTimeMillis() > expireTime.longValue()) {
      // 已过期，移除并视为未命中
      nullKeys.remove(key, expireTime);
      result = false;
    } else {
      // 命中空值缓存
      result = true;
    }
    return result;
  }

  /**
   * 将指定 key 计入空值缓存。
   *
   * @param key   缓存 key
   * @param ttlMs 空值缓存 TTL（毫秒）
   */
  public void put(String key, long ttlMs) {
    long expireTime = System.currentTimeMillis() + ttlMs;
    nullKeys.put(key, expireTime);
  }

  /**
   * 失效指定 key 的空值缓存。
   * <p>
   * 通常在该 key 对应数据被写入后调用，使后续请求能回源查到新数据。
   *
   * @param key 缓存 key
   */
  public void invalidate(String key) {
    nullKeys.remove(key);
  }

  /** 失效全部空值缓存条目 */
  public void invalidateAll() {
    nullKeys.clear();
  }

  /**
   * 获取当前空值缓存条目数。
   *
   * @return 条目数
   */
  public int size() {
    return nullKeys.size();
  }

  /**
   * 清理所有已过期的空值缓存条目。
   * <p>
   * 遍历并移除过期项，避免长期累积导致内存占用增长。
   */
  public void clearExpired() {
    long now = System.currentTimeMillis();
    for (Map.Entry<String, Long> entry : nullKeys.entrySet()) {
      if (now > entry.getValue().longValue()) {
        // 仅在值未变化时移除，避免并发覆盖
        nullKeys.remove(entry.getKey(), entry.getValue());
      }
    }
  }
}
