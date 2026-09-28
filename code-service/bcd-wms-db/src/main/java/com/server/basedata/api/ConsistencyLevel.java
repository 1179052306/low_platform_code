package com.server.basedata.api;

/**
 * 一致性级别枚举。
 * <p>
 * 定义基础数据读取时可选的一致性策略，决定查询链路是否经过缓存、
 * 以及在缓存与数据库之间如何取舍，用于在性能与一致性之间做权衡。
 * <ul>
 * <li>{@link #EVENTUAL} — 最终一致性：优先走缓存，容忍短暂的缓存与数据库不一致，性能最高</li>
 * <li>{@link #STRONGER} — 较强一致性：在最终一致性基础上增加校验/回源手段，降低读到脏数据的概率</li>
 * <li>{@link #BYPASS_CACHE} — 绕过缓存：直接查询数据库，用于刷新场景或对一致性要求最高的读取</li>
 * </ul>
 */
public enum ConsistencyLevel {
  /** 最终一致性：优先缓存，容忍短暂不一致 */
  EVENTUAL,
  /** 较强一致性：增加校验或回源，降低脏读概率 */
  STRONGER,
  /** 绕过缓存：直接查库，用于刷新或强一致读取 */
  BYPASS_CACHE
}
