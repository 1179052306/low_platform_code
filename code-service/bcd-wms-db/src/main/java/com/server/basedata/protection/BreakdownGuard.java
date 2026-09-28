package com.server.basedata.protection;

import com.server.basedata.api.CompactRow;

import java.util.function.Supplier;

/**
 * 综合防护器，组合多种缓存防护策略。
 * <p>
 * 将 {@link SingleFlight}（防击穿）、{@link CircuitBreaker}（熔断隔离）、
 * {@link NullCache}（防穿透）、{@link TtlJitter}（防雪崩）组合为统一入口，
 * 供加载链路调用。{@link #protectedLoad} 按顺序应用熔断→空值缓存→单飞，并在加载结果为空时
 * 计入空值缓存，加载异常时记录熔断失败。
 */
public class BreakdownGuard {

  /** 单飞合并器 */
  private final SingleFlight<CompactRow> singleFlight;
  /** 熔断器 */
  private final CircuitBreaker circuitBreaker;
  /** 空值缓存 */
  private final NullCache nullCache;
  /** TTL 抖动器 */
  private final TtlJitter ttlJitter;

  /**
   * 构造综合防护器。
   *
   * @param singleFlight   单飞合并器
   * @param circuitBreaker 熔断器
   * @param nullCache      空值缓存
   * @param ttlJitter      TTL 抖动器
   */
  public BreakdownGuard(SingleFlight<CompactRow> singleFlight,
      CircuitBreaker circuitBreaker,
      NullCache nullCache,
      TtlJitter ttlJitter) {
    this.singleFlight = singleFlight;
    this.circuitBreaker = circuitBreaker;
    this.nullCache = nullCache;
    this.ttlJitter = ttlJitter;
  }

  /**
   * 在综合防护下执行加载。
   * <p>
   * 流程：
   * <ol>
   * <li>熔断器拒绝时直接返回 null</li>
   * <li>命中空值缓存时直接返回 null</li>
   * <li>通过单飞执行实际加载，加载结果为空时计入空值缓存（默认 60 秒）</li>
   * <li>加载成功记录熔断成功；异常记录熔断失败并抛出</li>
   * </ol>
   *
   * @param key    防护键
   * @param loader 实际加载逻辑
   * @return 加载结果；熔断或空值缓存命中时返回 null
   */
  public CompactRow protectedLoad(String key, Supplier<CompactRow> loader) {
    CompactRow result;
    if (!circuitBreaker.allowRequest()) {
      // 熔断中，直接返回 null
      result = null;
    } else {
      if (nullCache.isNull(key)) {
        // 命中空值缓存，直接返回 null
        result = null;
      } else {
        try {
          // 单飞执行加载，结果为空时计入空值缓存
          CompactRow row = singleFlight.execute(key, () -> {
            CompactRow loaded = loader.get();
            if (loaded == null) {
              // 数据库未查到，计入空值缓存防穿透
              nullCache.put(key, 60000L);
            }
            return loaded;
          });
          circuitBreaker.recordSuccess();
          result = row;
        } catch (RuntimeException e) {
          // 加载异常，记录熔断失败并抛出
          circuitBreaker.recordFailure();
          throw e;
        }
      }
    }
    return result;
  }

  /**
   * 对 TTL 施加抖动（防雪崩）。
   *
   * @param ttlMs 原始 TTL（毫秒）
   * @return 抖动后的 TTL
   */
  public long jitterTtl(long ttlMs) {
    return ttlJitter.jitter(ttlMs);
  }

  /**
   * 失效指定 key 的空值缓存。
   *
   * @param key 缓存 key
   */
  public void invalidate(String key) {
    nullCache.invalidate(key);
  }

  /** 失效全部空值缓存条目 */
  public void invalidateAll() {
    nullCache.invalidateAll();
  }

  /**
   * 获取熔断器。
   *
   * @return 熔断器
   */
  public CircuitBreaker getCircuitBreaker() {
    return circuitBreaker;
  }

  /**
   * 获取空值缓存。
   *
   * @return 空值缓存
   */
  public NullCache getNullCache() {
    return nullCache;
  }

  /**
   * 获取单飞合并器。
   *
   * @return 单飞合并器
   */
  public SingleFlight<CompactRow> getSingleFlight() {
    return singleFlight;
  }
}
