package com.server.basedata.api;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存运行指标采集器。
 * <p>
 * 以无锁方式（{@link AtomicLong}）累计 L1/L2 命中与未命中次数、数据库加载次数与耗时、
 * 空值缓存命中、单飞合并、失效、熔断等计数，用于监控缓存健康度与性能调优。
 * <p>
 * 该类线程安全，可在多线程并发读写场景下直接调用记录方法。
 */
public class CacheMetrics {

  /** L1 命中次数 */
  private final AtomicLong l1HitCount = new AtomicLong();
  /** L1 未命中次数 */
  private final AtomicLong l1MissCount = new AtomicLong();
  /** L2 命中次数 */
  private final AtomicLong l2HitCount = new AtomicLong();
  /** L2 未命中次数 */
  private final AtomicLong l2MissCount = new AtomicLong();
  /** 数据库加载次数 */
  private final AtomicLong dbLoadCount = new AtomicLong();
  /** 数据库加载累计耗时（纳秒） */
  private final AtomicLong dbLoadTimeNs = new AtomicLong();
  /** 空值缓存命中次数（防穿透） */
  private final AtomicLong nullCacheCount = new AtomicLong();
  /** 单飞合并次数（防击穿） */
  private final AtomicLong singleFlightCount = new AtomicLong();
  /** 缓存失效次数 */
  private final AtomicLong invalidateCount = new AtomicLong();
  /** 熔断触发次数 */
  private final AtomicLong circuitBreakerCount = new AtomicLong();

  /** 记录一次 L1 命中 */
  public void recordL1Hit() {
    l1HitCount.incrementAndGet();
  }

  /** 记录一次 L1 未命中 */
  public void recordL1Miss() {
    l1MissCount.incrementAndGet();
  }

  /** 记录一次 L2 命中 */
  public void recordL2Hit() {
    l2HitCount.incrementAndGet();
  }

  /** 记录一次 L2 未命中 */
  public void recordL2Miss() {
    l2MissCount.incrementAndGet();
  }

  /**
   * 记录一次数据库加载及其耗时。
   *
   * @param elapsedNs 本次加载耗时（纳秒）
   */
  public void recordDbLoad(long elapsedNs) {
    dbLoadCount.incrementAndGet();
    dbLoadTimeNs.addAndGet(elapsedNs);
  }

  /** 记录一次空值缓存命中 */
  public void recordNullCache() {
    nullCacheCount.incrementAndGet();
  }

  /** 记录一次单飞合并 */
  public void recordSingleFlight() {
    singleFlightCount.incrementAndGet();
  }

  /** 记录一次缓存失效 */
  public void recordInvalidate() {
    invalidateCount.incrementAndGet();
  }

  /** 记录一次熔断触发 */
  public void recordCircuitBreaker() {
    circuitBreakerCount.incrementAndGet();
  }

  /**
   * 获取 L1 命中次数。
   *
   * @return L1 命中次数
   */
  public long getL1HitCount() {
    return l1HitCount.get();
  }

  /**
   * 获取 L1 未命中次数。
   *
   * @return L1 未命中次数
   */
  public long getL1MissCount() {
    return l1MissCount.get();
  }

  /**
   * 获取 L2 命中次数。
   *
   * @return L2 命中次数
   */
  public long getL2HitCount() {
    return l2HitCount.get();
  }

  /**
   * 获取 L2 未命中次数。
   *
   * @return L2 未命中次数
   */
  public long getL2MissCount() {
    return l2MissCount.get();
  }

  /**
   * 获取数据库加载次数。
   *
   * @return 数据库加载次数
   */
  public long getDbLoadCount() {
    return dbLoadCount.get();
  }

  /**
   * 获取空值缓存命中次数。
   *
   * @return 空值缓存命中次数
   */
  public long getNullCacheCount() {
    return nullCacheCount.get();
  }

  /**
   * 获取单飞合并次数。
   *
   * @return 单飞合并次数
   */
  public long getSingleFlightCount() {
    return singleFlightCount.get();
  }

  /**
   * 获取缓存失效次数。
   *
   * @return 缓存失效次数
   */
  public long getInvalidateCount() {
    return invalidateCount.get();
  }

  /**
   * 获取熔断触发次数。
   *
   * @return 熔断触发次数
   */
  public long getCircuitBreakerCount() {
    return circuitBreakerCount.get();
  }

  /**
   * 计算 L1 命中率。
   *
   * @return L1 命中率，[0.0, 1.0]；无样本时返回 0.0
   */
  public double getL1HitRate() {
    return computeHitRate(l1HitCount.get(), l1MissCount.get());
  }

  /**
   * 计算 L2 命中率。
   *
   * @return L2 命中率，[0.0, 1.0]；无样本时返回 0.0
   */
  public double getL2HitRate() {
    return computeHitRate(l2HitCount.get(), l2MissCount.get());
  }

  /**
   * 计算数据库平均加载耗时（毫秒）。
   *
   * @return 平均加载耗时毫秒数；无样本时返回 0.0
   */
  public double getAvgDbLoadMillis() {
    double result;
    long count = dbLoadCount.get();
    if (count == 0) {
      // 无样本，返回 0 避免除零
      result = 0.0;
    } else {
      // 累计纳秒 / 次数 / 1e6 得到平均毫秒
      result = (double) dbLoadTimeNs.get() / count / 1000000.0;
    }
    return result;
  }

  /**
   * 计算命中率。
   *
   * @param hit  命中次数
   * @param miss 未命中次数
   * @return 命中率，[0.0, 1.0]；总次数为 0 时返回 0.0
   */
  private double computeHitRate(long hit, long miss) {
    double result;
    long total = hit + miss;
    if (total == 0) {
      // 无样本，返回 0 避免除零
      result = 0.0;
    } else {
      result = (double) hit / total;
    }
    return result;
  }

  /**
   * 生成当前指标快照。
   * <p>
   * 将各计数器当前值复制到一个新的 {@link CacheMetrics} 实例，返回的对象与原实例互不影响，
   * 适合上报或对比计算。
   *
   * @return 指标快照
   */
  public CacheMetrics snapshot() {
    CacheMetrics snap = new CacheMetrics();
    snap.l1HitCount.set(this.l1HitCount.get());
    snap.l1MissCount.set(this.l1MissCount.get());
    snap.l2HitCount.set(this.l2HitCount.get());
    snap.l2MissCount.set(this.l2MissCount.get());
    snap.dbLoadCount.set(this.dbLoadCount.get());
    snap.dbLoadTimeNs.set(this.dbLoadTimeNs.get());
    snap.nullCacheCount.set(this.nullCacheCount.get());
    snap.singleFlightCount.set(this.singleFlightCount.get());
    snap.invalidateCount.set(this.invalidateCount.get());
    snap.circuitBreakerCount.set(this.circuitBreakerCount.get());
    return snap;
  }
}
