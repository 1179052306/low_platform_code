package com.server.basedata.api;

/**
 * 基础数据查询结果包装。
 * <p>
 * 在 {@link CompactRow} 之上附加命中来源与耗时信息，用于链路观测、
 * 命中率统计与慢查排查。{@link Source} 标识数据来自哪一层（L1/L2/DB/空缓存/未命中），
 * elapsedNanos 记录本次读取的纳秒耗时。
 */
public final class BaseDataResult {

  /**
   * 数据来源枚举。
   * <p>
   * 按访问链路顺序定义，便于在日志与指标中直观判断命中层级。
   */
  public enum Source {
    /** 命中 L1 本地缓存（Caffeine） */
    L1_CACHE,
    /** 命中 L2 Redis 缓存 */
    L2_REDIS,
    /** 回源数据库加载 */
    DATABASE,
    /** 命中空值缓存（防穿透） */
    NULL_CACHE,
    /** 未命中任何层且未回源 */
    MISS
  }

  /** 查询到的紧凑行，可能为 null */
  private final CompactRow row;
  /** 数据来源 */
  private final Source source;
  /** 本次读取耗时（纳秒） */
  private final long elapsedNanos;

  /**
   * 构造查询结果。
   *
   * @param row          紧凑行，可为 null
   * @param source       数据来源
   * @param elapsedNanos 耗时（纳秒）
   */
  public BaseDataResult(CompactRow row, Source source, long elapsedNanos) {
    this.row = row;
    this.source = source;
    this.elapsedNanos = elapsedNanos;
  }

  /**
   * 获取紧凑行。
   *
   * @return 紧凑行，可能为 null
   */
  public CompactRow getRow() {
    return row;
  }

  /**
   * 获取数据来源。
   *
   * @return 数据来源
   */
  public Source getSource() {
    return source;
  }

  /**
   * 获取耗时（纳秒）。
   *
   * @return 耗时纳秒数
   */
  public long getElapsedNanos() {
    return elapsedNanos;
  }

  /**
   * 获取耗时（毫秒）。
   * <p>
   * 由纳秒值整除 1_000_000 得到，向下取整。
   *
   * @return 耗时毫秒数
   */
  public long getElapsedMillis() {
    return elapsedNanos / 1000000L;
  }

  /**
   * 判断是否命中缓存（含空值缓存与回源）。
   * <p>
   * 仅当来源为 {@link Source#MISS} 时视为未命中，其余来源（含 NULL_CACHE、DATABASE）均视为命中。
   *
   * @return 命中返回 true，未命中返回 false
   */
  public boolean isHit() {
    boolean result;
    if (source == Source.MISS) {
      // 未命中任何层
      result = false;
    } else {
      result = true;
    }
    return result;
  }
}
