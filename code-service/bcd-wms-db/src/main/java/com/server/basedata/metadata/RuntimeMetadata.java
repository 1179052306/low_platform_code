package com.server.basedata.metadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时元数据。
 * <p>
 * 一次编译产出的不可变元数据快照，包含全部实体、投影、关联的预编译结果，并以版本号标识。
 * 通过 {@link VersionManager} 原子切换，实现元数据的热更新与多版本共存。
 * <p>
 * 所有内部映射在构造时包装为不可变，保证发布后不被修改。
 */
public final class RuntimeMetadata {

  /** 元数据版本号 */
  private final long version;
  /** 缓存空间名 → 预编译实体 */
  private final Map<String, CompiledEntity> entityMap;
  /** 缓存空间名 → (投影名 → 预编译投影) */
  private final Map<String, Map<String, CompiledProjection>> projectionMap;
  /** 缓存空间名 → 关联列表 */
  private final Map<String, List<CompiledRelation>> relationMap;

  /**
   * 构造运行时元数据。
   * <p>
   * 将传入的各映射包装为不可变，确保发布后只读。
   *
   * @param version       版本号
   * @param entityMap     实体映射
   * @param projectionMap 投影映射
   * @param relationMap   关联映射
   */
  public RuntimeMetadata(long version,
      Map<String, CompiledEntity> entityMap,
      Map<String, Map<String, CompiledProjection>> projectionMap,
      Map<String, List<CompiledRelation>> relationMap) {
    this.version = version;
    this.entityMap = Collections.unmodifiableMap(entityMap);
    this.projectionMap = Collections.unmodifiableMap(projectionMap);
    this.relationMap = Collections.unmodifiableMap(relationMap);
  }

  /**
   * 获取版本号。
   *
   * @return 版本号
   */
  public long getVersion() {
    return version;
  }

  /**
   * 按缓存空间名获取实体。
   *
   * @param cacheName 缓存空间名
   * @return 预编译实体；不存在时返回 null
   */
  public CompiledEntity getEntity(String cacheName) {
    return entityMap.get(cacheName);
  }

  /**
   * 按缓存空间名与投影名获取投影。
   *
   * @param cacheName      缓存空间名
   * @param projectionName 投影名
   * @return 预编译投影；不存在时返回 null
   */
  public CompiledProjection getProjection(String cacheName, String projectionName) {
    Map<String, CompiledProjection> projections = projectionMap.get(cacheName);
    CompiledProjection result;
    if (projections == null) {
      // 该缓存空间无任何投影
      result = null;
    } else {
      result = projections.get(projectionName);
    }
    return result;
  }

  /**
   * 按缓存空间名获取关联列表。
   *
   * @param cacheName 缓存空间名
   * @return 关联列表；无关联时返回空列表（非 null）
   */
  public List<CompiledRelation> getRelations(String cacheName) {
    List<CompiledRelation> result;
    List<CompiledRelation> relations = relationMap.get(cacheName);
    if (relations == null) {
      // 无关联返回空列表，避免调用方判空
      result = Collections.emptyList();
    } else {
      result = relations;
    }
    return result;
  }

  /**
   * 判断指定缓存空间是否存在对应实体。
   *
   * @param cacheName 缓存空间名
   * @return 存在返回 true
   */
  public boolean hasEntity(String cacheName) {
    return entityMap.containsKey(cacheName);
  }

  /**
   * 获取实体总数。
   *
   * @return 实体数量
   */
  public int getEntityCount() {
    return entityMap.size();
  }

  /**
   * 获取全部缓存空间名集合。
   *
   * @return 缓存空间名集合
   */
  public java.util.Set<String> getAllCacheNames() {
    return entityMap.keySet();
  }
}
