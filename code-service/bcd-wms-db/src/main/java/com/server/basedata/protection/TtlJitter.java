package com.server.basedata.protection;

import java.util.concurrent.ThreadLocalRandom;

/**
 * TTL 抖动器，用于缓存雪崩防护。
 * <p>
 * 给基础 TTL 附加一个随机扰动因子，使大量 key 的过期时间分散开，避免同一时刻集中失效
 * 导致回源请求骤增。扰动幅度由 ratio 控制，最终 TTL 落在
 * {@code [ttl*(1-ratio), ttl*(1+ratio)]} 区间内。
 */
public class TtlJitter {

  /** 抖动比例，最终 TTL 在 ±ratio 范围内随机 */
  private final double ratio;

  /**
   * 使用默认抖动比例 0.1（±10%）构造。
   */
  public TtlJitter() {
    this(0.1);
  }

  /**
   * 指定抖动比例构造。
   *
   * @param ratio 抖动比例，如 0.1 表示 ±10%
   */
  public TtlJitter(double ratio) {
    this.ratio = ratio;
  }

  /**
   * 对毫秒级 TTL 施加抖动。
   *
   * @param ttlMs 原始 TTL（毫秒）
   * @return 抖动后的 TTL（毫秒）
   */
  public long jitter(long ttlMs) {
    double min = 1.0 - ratio;
    double max = 1.0 + ratio;
    double factor = ThreadLocalRandom.current().nextDouble(min, max);
    return (long) (ttlMs * factor);
  }

  /**
   * 对秒级 TTL 施加抖动。
   *
   * @param ttlSeconds 原始 TTL（秒）
   * @return 抖动后的 TTL（秒）
   */
  public int jitter(int ttlSeconds) {
    double min = 1.0 - ratio;
    double max = 1.0 + ratio;
    double factor = ThreadLocalRandom.current().nextDouble(min, max);
    return (int) (ttlSeconds * factor);
  }
}
