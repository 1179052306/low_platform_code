package com.server.basedata.metadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 预编译实体。
 * <p>
 * 将基础数据实体的元数据（表名、主键列、字段名顺序、加密标记、TTL、容量、版本等）
 * 预编译为不可变结构，供运行时高效查询字段下标、构造缓存 key、编解码与回源查询使用。
 * <p>
 * 通过 {@link #fieldToIndex} 提供 O(1) 的字段名到下标映射，避免运行时线性查找。
 */
public final class CompiledEntity {

  /** 缓存空间名 */
  private final String cacheName;
  /** 数据库表名 */
  private final String tableName;
  /** 主键列名 */
  private final String idColumn;
  /** 业务编码列名（可选，用于按 code 查询） */
  private final String codeColumn;
  /** 字段名数组，下标对应 CompactRow.values 的位置 */
  private final String[] fieldNames;
  /** 字段名到下标的不可变映射 */
  private final Map<String, Integer> fieldToIndex;
  /** 结构版本号 */
  private final int schemaVersion;
  /** 结构哈希（用于变更检测） */
  private final String schemaHash;
  /** 各字段是否加密的标记数组 */
  private final boolean[] encryptedFlags;
  /** 各字段独立 TTL 数组（秒） */
  private final int[] fieldTtls;
  /** 实体默认 TTL（秒） */
  private final int ttl;
  /** 缓存最大容量 */
  private final int maxCapacity;
  /** 是否启用 */
  private final boolean enabled;

  /**
   * 构造预编译实体。
   * <p>
   * 在构造时根据 fieldNames 构建 字段名→下标 的不可变映射，供后续 O(1) 查找。
   *
   * @param cacheName      缓存空间名
   * @param tableName      表名
   * @param idColumn       主键列名
   * @param codeColumn     业务编码列名
   * @param fieldNames     字段名数组
   * @param schemaVersion  结构版本号
   * @param schemaHash     结构哈希
   * @param encryptedFlags 加密标记数组
   * @param fieldTtls      字段独立 TTL 数组
   * @param ttl            实体默认 TTL
   * @param maxCapacity    缓存最大容量
   * @param enabled        是否启用
   */
  public CompiledEntity(String cacheName, String tableName, String idColumn,
      String codeColumn, String[] fieldNames, int schemaVersion,
      String schemaHash, boolean[] encryptedFlags, int[] fieldTtls,
      int ttl, int maxCapacity, boolean enabled) {
    this.cacheName = cacheName;
    this.tableName = tableName;
    this.idColumn = idColumn;
    this.codeColumn = codeColumn;
    this.fieldNames = fieldNames;
    this.schemaVersion = schemaVersion;
    this.schemaHash = schemaHash;
    this.encryptedFlags = encryptedFlags;
    this.fieldTtls = fieldTtls;
    this.ttl = ttl;
    this.maxCapacity = maxCapacity;
    this.enabled = enabled;

    // 构建字段名到下标的映射，并包装为不可变
    Map<String, Integer> idxMap = new HashMap<String, Integer>();
    if (fieldNames != null) {
      for (int i = 0; i < fieldNames.length; i++) {
        idxMap.put(fieldNames[i], i);
      }
    }
    this.fieldToIndex = Collections.unmodifiableMap(idxMap);
  }

  /**
   * 按字段名获取下标。
   *
   * @param fieldName 字段名
   * @return 字段下标；不存在时返回 -1
   */
  public int getFieldIndex(String fieldName) {
    Integer idx = fieldToIndex.get(fieldName);
    int result;
    if (idx == null) {
      // 字段不存在，返回 -1 便于调用方判断
      result = -1;
    } else {
      result = idx.intValue();
    }
    return result;
  }

  /**
   * 获取缓存空间名。
   *
   * @return 缓存空间名
   */
  public String getCacheName() {
    return cacheName;
  }

  /**
   * 获取表名。
   *
   * @return 表名
   */
  public String getTableName() {
    return tableName;
  }

  /**
   * 获取主键列名。
   *
   * @return 主键列名
   */
  public String getIdColumn() {
    return idColumn;
  }

  /**
   * 获取业务编码列名。
   *
   * @return 业务编码列名
   */
  public String getCodeColumn() {
    return codeColumn;
  }

  /**
   * 获取字段名数组。
   *
   * @return 字段名数组
   */
  public String[] getFieldNames() {
    return fieldNames;
  }

  /**
   * 获取结构版本号。
   *
   * @return 结构版本号
   */
  public int getSchemaVersion() {
    return schemaVersion;
  }

  /**
   * 获取结构哈希。
   *
   * @return 结构哈希
   */
  public String getSchemaHash() {
    return schemaHash;
  }

  /**
   * 获取加密标记数组。
   *
   * @return 加密标记数组
   */
  public boolean[] getEncryptedFlags() {
    return encryptedFlags;
  }

  /**
   * 获取字段独立 TTL 数组。
   *
   * @return 字段 TTL 数组
   */
  public int[] getFieldTtls() {
    return fieldTtls;
  }

  /**
   * 获取实体默认 TTL。
   *
   * @return 默认 TTL（秒）
   */
  public int getTtl() {
    return ttl;
  }

  /**
   * 获取缓存最大容量。
   *
   * @return 最大容量
   */
  public int getMaxCapacity() {
    return maxCapacity;
  }

  /**
   * 判断实体是否启用。
   *
   * @return 启用返回 true
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * 获取字段数量。
   *
   * @return 字段数量；fieldNames 为 null 时返回 0
   */
  public int getFieldCount() {
    int result;
    if (fieldNames == null) {
      result = 0;
    } else {
      result = fieldNames.length;
    }
    return result;
  }
}
