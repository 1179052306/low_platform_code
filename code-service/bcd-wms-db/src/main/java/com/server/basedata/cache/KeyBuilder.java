package com.server.basedata.cache;

/**
 * Redis Key 构建器。
 * <p>
 * 统一构造基础数据在 Redis 中的存储 key 与扫描 pattern，确保 L2 缓存的 key 命名规范一致、
 * 可追溯、可按缓存空间或结构版本批量定位。key 格式遵循
 * {@code bd:<cacheName>:s<schemaVersion>:<id>}，其中 {@code bd} 为基础数据命名空间，
 * {@code s<schemaVersion>} 用于多版本缓存隔离，避免结构变更后读到旧格式数据。
 */
public class KeyBuilder {

  /** 基础数据命名空间前缀 */
  private static final String NAMESPACE = "bd";
  /** 结构版本段前缀 */
  private static final String SCHEMA_PREFIX = "s";

  /**
   * 构造单个缓存条目的完整 key。
   *
   * @param cacheName     缓存空间名（通常对应实体名）
   * @param schemaVersion 结构版本号
   * @param id            主键值
   * @return 形如 {@code bd:<cacheName>:s<schemaVersion>:<id>} 的 key
   */
  public String buildKey(String cacheName, int schemaVersion, Object id) {
    return NAMESPACE + ":" + cacheName + ":" + SCHEMA_PREFIX + schemaVersion + ":" + id;
  }

  /**
   * 构造指定缓存空间与结构版本下的扫描 pattern。
   * <p>
   * 用于批量失效某一结构版本下的所有条目。
   *
   * @param cacheName     缓存空间名
   * @param schemaVersion 结构版本号
   * @return 形如 {@code bd:<cacheName>:s<schemaVersion>:*} 的 pattern
   */
  public String buildPatternForSchemaVersion(String cacheName, int schemaVersion) {
    return NAMESPACE + ":" + cacheName + ":" + SCHEMA_PREFIX + schemaVersion + ":*";
  }

  /**
   * 构造指定缓存空间下所有结构版本的扫描 pattern。
   * <p>
   * 用于批量失效某一缓存空间下的全部条目（跨结构版本）。
   *
   * @param cacheName 缓存空间名
   * @return 形如 {@code bd:<cacheName>:*} 的 pattern
   */
  public String buildPatternForCacheName(String cacheName) {
    return NAMESPACE + ":" + cacheName + ":*";
  }
}
