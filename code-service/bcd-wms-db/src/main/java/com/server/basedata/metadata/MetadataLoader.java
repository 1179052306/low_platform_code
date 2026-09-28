package com.server.basedata.metadata;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.SqlEngine;
import com.server.sqlengine.executor.SqlExecutor;
import com.server.sqlengine.model.CompiledSql;
import com.server.sqlengine.model.Condition;
import com.server.sqlengine.model.SelectQuery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 元数据加载器。
 * <p>
 * 从数据库的元数据表（base_data_entity / base_data_field / base_data_relation /
 * base_data_projection / base_data_cache）读取原始元数据行，组装为 {@link RawMetadata}
 * 供 {@link MetadataCompiler} 编译。加载过程仅做读取与行映射，不做字段下标解析等编译工作。
 * <p>
 * 所有查询只读取 enabled = TRUE 的记录，通过 sqlengine 编译 SQL 并由 SqlExecutor 执行。
 */
public class MetadataLoader {

  /** SQL 引擎 */
  private final SqlEngine sqlEngine;
  /** SQL 执行器 */
  private final SqlExecutor sqlExecutor;

  /**
   * 构造加载器。
   *
   * @param sqlEngine   SQL 引擎
   * @param sqlExecutor SQL 执行器
   */
  public MetadataLoader(SqlEngine sqlEngine, SqlExecutor sqlExecutor) {
    this.sqlEngine = sqlEngine;
    this.sqlExecutor = sqlExecutor;
  }

  /**
   * 原始元数据聚合容器。
   * <p>
   * 按表分组存放加载到的实体、字段、关联、投影与缓存配置行。
   */
  public static final class RawMetadata {
    /** 实体行列表 */
    public final List<EntityRow> entities = new ArrayList<EntityRow>();
    /** 字段行列表 */
    public final List<FieldRow> fields = new ArrayList<FieldRow>();
    /** 关联行列表 */
    public final List<RelationRow> relations = new ArrayList<RelationRow>();
    /** 投影行列表 */
    public final List<ProjectionRow> projections = new ArrayList<ProjectionRow>();
    /** 缓存配置映射（缓存空间名 → 配置） */
    public final Map<String, CacheConfigRow> cacheConfigs = new HashMap<String, CacheConfigRow>();
  }

  /**
   * 实体元数据行。
   */
  public static final class EntityRow {
    /** 缓存空间名 */
    public final String cacheName;
    /** 实体名 */
    public final String entityName;
    /** 表名 */
    public final String tableName;
    /** 主键列名 */
    public final String idColumn;
    /** 业务编码列名 */
    public final String codeColumn;
    /** 默认 TTL（秒） */
    public final int ttl;
    /** 缓存最大容量 */
    public final int maxCapacity;
    /** 结构版本号 */
    public final int schemaVersion;
    /** 结构哈希 */
    public final String schemaHash;

    /**
     * 构造实体行。
     *
     * @param cacheName     缓存空间名
     * @param entityName    实体名
     * @param tableName     表名
     * @param idColumn      主键列名
     * @param codeColumn    业务编码列名
     * @param ttl           默认 TTL
     * @param maxCapacity   最大容量
     * @param schemaVersion 结构版本号
     * @param schemaHash    结构哈希
     */
    public EntityRow(String cacheName, String entityName, String tableName,
        String idColumn, String codeColumn, int ttl, int maxCapacity,
        int schemaVersion, String schemaHash) {
      this.cacheName = cacheName;
      this.entityName = entityName;
      this.tableName = tableName;
      this.idColumn = idColumn;
      this.codeColumn = codeColumn;
      this.ttl = ttl;
      this.maxCapacity = maxCapacity;
      this.schemaVersion = schemaVersion;
      this.schemaHash = schemaHash;
    }
  }

  /**
   * 字段元数据行。
   */
  public static final class FieldRow {
    /** 所属缓存空间名 */
    public final String cacheName;
    /** 列名 */
    public final String columnName;
    /** 数据类型 */
    public final String dataType;
    /** 列下标 */
    public final int columnIndex;
    /** 是否加密 */
    public final boolean isEncrypted;
    /** 字段独立 TTL（秒），可为 null 表示沿用实体默认 */
    public final Integer fieldTtl;

    /**
     * 构造字段行。
     *
     * @param cacheName   缓存空间名
     * @param columnName  列名
     * @param dataType    数据类型
     * @param columnIndex 列下标
     * @param isEncrypted 是否加密
     * @param fieldTtl    字段独立 TTL，可为 null
     */
    public FieldRow(String cacheName, String columnName, String dataType,
        int columnIndex, boolean isEncrypted, Integer fieldTtl) {
      this.cacheName = cacheName;
      this.columnName = columnName;
      this.dataType = dataType;
      this.columnIndex = columnIndex;
      this.isEncrypted = isEncrypted;
      this.fieldTtl = fieldTtl;
    }
  }

  /**
   * 关联元数据行。
   */
  public static final class RelationRow {
    /** 源缓存空间名 */
    public final String sourceCache;
    /** 源列名 */
    public final String sourceColumn;
    /** 目标缓存空间名 */
    public final String targetCache;
    /** 目标列名 */
    public final String targetColumn;
    /** 关联类型 */
    public final String relationType;

    /**
     * 构造关联行。
     *
     * @param sourceCache  源缓存空间名
     * @param sourceColumn 源列名
     * @param targetCache  目标缓存空间名
     * @param targetColumn 目标列名
     * @param relationType 关联类型
     */
    public RelationRow(String sourceCache, String sourceColumn,
        String targetCache, String targetColumn, String relationType) {
      this.sourceCache = sourceCache;
      this.sourceColumn = sourceColumn;
      this.targetCache = targetCache;
      this.targetColumn = targetColumn;
      this.relationType = relationType;
    }
  }

  /**
   * 投影元数据行。
   */
  public static final class ProjectionRow {
    /** 所属缓存空间名 */
    public final String cacheName;
    /** 投影名 */
    public final String projectionName;
    /** 投影包含的字段名列表 */
    public final List<String> fields;

    /**
     * 构造投影行。
     *
     * @param cacheName      缓存空间名
     * @param projectionName 投影名
     * @param fields         字段名列表
     */
    public ProjectionRow(String cacheName, String projectionName, List<String> fields) {
      this.cacheName = cacheName;
      this.projectionName = projectionName;
      this.fields = fields;
    }
  }

  /**
   * 缓存配置行。
   * <p>
   * 承载每个缓存空间的运行参数：TTL、容量、各级缓存开关、防护策略、限流阈值、降级策略等。
   */
  public static final class CacheConfigRow {
    /** 缓存空间名 */
    public final String cacheName;
    /** 默认 TTL（秒） */
    public final int ttl;
    /** 最大容量 */
    public final int maxCapacity;
    /** 是否启用 L1 本地缓存 */
    public final boolean enableLocalCache;
    /** 是否启用空值缓存（防穿透） */
    public final boolean enableNullCache;
    /** 是否启用综合防护 */
    public final boolean enableProtection;
    /** 批量查询批大小 */
    public final int batchSize;
    /** 是否启用 TTL 随机抖动（防雪崩） */
    public final boolean randomTtl;
    /** 最大关联展开深度 */
    public final int maxRelationDepth;
    /** 空值缓存 TTL（秒） */
    public final int nullTtl;
    /** 是否启用 SWR（Stale-While-Revalidate） */
    public final boolean enableSwr;
    /** SWR 刷新 TTL（秒） */
    public final int swrRefreshTtl;
    /** 是否启用熔断器 */
    public final boolean enableCircuitBreaker;
    /** 熔断失败阈值 */
    public final int cbFailureThreshold;
    /** 熔断恢复时间（毫秒） */
    public final int cbRecoveryMs;
    /** 缓存准入阈值 */
    public final int admissionThreshold;
    /** 批量最大主键数 */
    public final int maxBatchIds;
    /** 最大结果行数 */
    public final int maxResultRows;
    /** 最大负载字节数 */
    public final int maxPayloadBytes;
    /** 一致性级别 */
    public final String consistencyLevel;
    /** 降级策略 */
    public final String degradePolicy;
    /** 版本修订策略 */
    public final String revisionStrategy;

    /**
     * 构造缓存配置行。
     *
     * @param cacheName            缓存空间名
     * @param ttl                  默认 TTL
     * @param maxCapacity          最大容量
     * @param enableLocalCache     是否启用 L1
     * @param enableNullCache      是否启用空值缓存
     * @param enableProtection     是否启用防护
     * @param batchSize            批大小
     * @param randomTtl            是否启用 TTL 抖动
     * @param maxRelationDepth     最大关联深度
     * @param nullTtl              空值缓存 TTL
     * @param enableSwr            是否启用 SWR
     * @param swrRefreshTtl        SWR 刷新 TTL
     * @param enableCircuitBreaker 是否启用熔断
     * @param cbFailureThreshold   熔断失败阈值
     * @param cbRecoveryMs         熔断恢复时间
     * @param admissionThreshold   准入阈值
     * @param maxBatchIds          批量最大主键数
     * @param maxResultRows        最大结果行数
     * @param maxPayloadBytes      最大负载字节数
     * @param consistencyLevel     一致性级别
     * @param degradePolicy        降级策略
     * @param revisionStrategy     版本修订策略
     */
    public CacheConfigRow(String cacheName, int ttl, int maxCapacity,
        boolean enableLocalCache, boolean enableNullCache,
        boolean enableProtection, int batchSize, boolean randomTtl,
        int maxRelationDepth, int nullTtl, boolean enableSwr,
        int swrRefreshTtl, boolean enableCircuitBreaker,
        int cbFailureThreshold, int cbRecoveryMs, int admissionThreshold,
        int maxBatchIds, int maxResultRows, int maxPayloadBytes,
        String consistencyLevel, String degradePolicy, String revisionStrategy) {
      this.cacheName = cacheName;
      this.ttl = ttl;
      this.maxCapacity = maxCapacity;
      this.enableLocalCache = enableLocalCache;
      this.enableNullCache = enableNullCache;
      this.enableProtection = enableProtection;
      this.batchSize = batchSize;
      this.randomTtl = randomTtl;
      this.maxRelationDepth = maxRelationDepth;
      this.nullTtl = nullTtl;
      this.enableSwr = enableSwr;
      this.swrRefreshTtl = swrRefreshTtl;
      this.enableCircuitBreaker = enableCircuitBreaker;
      this.cbFailureThreshold = cbFailureThreshold;
      this.cbRecoveryMs = cbRecoveryMs;
      this.admissionThreshold = admissionThreshold;
      this.maxBatchIds = maxBatchIds;
      this.maxResultRows = maxResultRows;
      this.maxPayloadBytes = maxPayloadBytes;
      this.consistencyLevel = consistencyLevel;
      this.degradePolicy = degradePolicy;
      this.revisionStrategy = revisionStrategy;
    }
  }

  /**
   * 加载全部原始元数据。
   * <p>
   * 依次加载实体、字段、关联、投影、缓存配置，通过 sqlengine 编译 SQL 并由 SqlExecutor 执行。
   *
   * @return 原始元数据聚合
   * @throws Exception 加载过程中抛出的 SQL 异常
   */
  public RawMetadata load() throws Exception {
    RawMetadata result = new RawMetadata();
    loadEntities(result);
    loadFields(result);
    loadRelations(result);
    loadProjections(result);
    loadCacheConfigs(result);
    return result;
  }

  /**
   * 加载启用的实体行。
   *
   * @param result 原始元数据容器
   * @throws Exception SQL 异常
   */
  private void loadEntities(RawMetadata result) throws Exception {
    // 构建 SELECT 查询：仅读取 enabled = TRUE 的实体行
    SelectQuery query = SelectQuery.builder()
        .from("BASE_DATA_ENTITY")
        .columns("CACHE_NAME", "ENTITY_NAME", "TABLE_NAME", "ID_COLUMN",
            "CODE_COLUMN", "TTL", "MAX_CAPACITY", "SCHEMA_VERSION", "SCHEMA_HASH")
        .where(Condition.eq("ENABLED", true))
        .build();
    CompiledSql compiled = sqlEngine.select(query, "lowcode");
    JSONArray rows = sqlExecutor.query("lowcode", compiled);
    for (int i = 0; i < rows.size(); i++) {
      JSONObject row = rows.getJSONObject(i);
      EntityRow entityRow = new EntityRow(
          row.getString("CACHE_NAME"),
          row.getString("ENTITY_NAME"),
          row.getString("TABLE_NAME"),
          row.getString("ID_COLUMN"),
          row.getString("CODE_COLUMN"),
          row.getIntValue("TTL"),
          row.getIntValue("MAX_CAPACITY"),
          row.getIntValue("SCHEMA_VERSION"),
          row.getString("SCHEMA_HASH"));
      result.entities.add(entityRow);
    }
  }

  /**
   * 加载启用的字段行，按缓存空间与列下标排序。
   *
   * @param result 原始元数据容器
   * @throws Exception SQL 异常
   */
  private void loadFields(RawMetadata result) throws Exception {
    // 构建 SELECT 查询：仅读取 enabled = TRUE 的字段行，按缓存空间与列下标排序
    SelectQuery query = SelectQuery.builder()
        .from("BASE_DATA_FIELD")
        .columns("CACHE_NAME", "COLUMN_NAME", "DATA_TYPE", "COLUMN_INDEX",
            "IS_ENCRYPTED", "FIELD_TTL")
        .where(Condition.eq("ENABLED", true))
        .orderByAsc("CACHE_NAME")
        .orderByAsc("COLUMN_INDEX")
        .build();
    CompiledSql compiled = sqlEngine.select(query, "lowcode");
    JSONArray rows = sqlExecutor.query("lowcode", compiled);
    for (int i = 0; i < rows.size(); i++) {
      JSONObject row = rows.getJSONObject(i);
      FieldRow fieldRow = new FieldRow(
          row.getString("CACHE_NAME"),
          row.getString("COLUMN_NAME"),
          row.getString("DATA_TYPE"),
          row.getIntValue("COLUMN_INDEX"),
          row.getBooleanValue("IS_ENCRYPTED"),
          row.getInteger("FIELD_TTL"));
      result.fields.add(fieldRow);
    }
  }

  /**
   * 加载启用的关联行。
   *
   * @param result 原始元数据容器
   * @throws Exception SQL 异常
   */
  private void loadRelations(RawMetadata result) throws Exception {
    // 构建 SELECT 查询：仅读取 enabled = TRUE 的关联行
    SelectQuery query = SelectQuery.builder()
        .from("BASE_DATA_RELATION")
        .columns("SOURCE_CACHE", "SOURCE_COLUMN", "TARGET_CACHE",
            "TARGET_COLUMN", "RELATION_TYPE")
        .where(Condition.eq("ENABLED", true))
        .build();
    CompiledSql compiled = sqlEngine.select(query, "lowcode");
    JSONArray rows = sqlExecutor.query("lowcode", compiled);
    for (int i = 0; i < rows.size(); i++) {
      JSONObject row = rows.getJSONObject(i);
      RelationRow relationRow = new RelationRow(
          row.getString("SOURCE_CACHE"),
          row.getString("SOURCE_COLUMN"),
          row.getString("TARGET_CACHE"),
          row.getString("TARGET_COLUMN"),
          row.getString("RELATION_TYPE"));
      result.relations.add(relationRow);
    }
  }

  /**
   * 加载启用的投影行，fields 列以 JSON 数组存储，使用 fastjson2 解析为字符串列表。
   *
   * @param result 原始元数据容器
   * @throws Exception SQL 异常
   */
  private void loadProjections(RawMetadata result) throws Exception {
    // 构建 SELECT 查询：仅读取 enabled = TRUE 的投影行
    SelectQuery query = SelectQuery.builder()
        .from("BASE_DATA_PROJECTION")
        .columns("CACHE_NAME", "PROJECTION_NAME", "FIELDS")
        .where(Condition.eq("ENABLED", true))
        .build();
    CompiledSql compiled = sqlEngine.select(query, "lowcode");
    JSONArray rows = sqlExecutor.query("lowcode", compiled);
    for (int i = 0; i < rows.size(); i++) {
      JSONObject row = rows.getJSONObject(i);
      // fields 列存储 JSON 数组字符串，解析为字段名列表
      String fieldsJson = row.getString("FIELDS");
      List<String> fields = JSONArray.parseArray(fieldsJson, String.class);
      ProjectionRow projectionRow = new ProjectionRow(
          row.getString("CACHE_NAME"),
          row.getString("PROJECTION_NAME"),
          fields);
      result.projections.add(projectionRow);
    }
  }

  /**
   * 加载缓存配置行。
   *
   * @param result 原始元数据容器
   * @throws Exception SQL 异常
   */
  private void loadCacheConfigs(RawMetadata result) throws Exception {
    // 构建 SELECT 查询：读取全部缓存配置行
    SelectQuery query = SelectQuery.builder()
        .from("BASE_DATA_CACHE")
        .columns("CACHE_NAME", "TTL", "MAX_CAPACITY", "ENABLE_LOCAL_CACHE",
            "ENABLE_NULL_CACHE", "ENABLE_PROTECTION", "BATCH_SIZE", "RANDOM_TTL",
            "MAX_RELATION_DEPTH", "NULL_TTL", "ENABLE_SWR", "SWR_REFRESH_TTL",
            "ENABLE_CIRCUIT_BREAKER", "CB_FAILURE_THRESHOLD", "CB_RECOVERY_MS",
            "ADMISSION_THRESHOLD", "MAX_BATCH_IDS", "MAX_RESULT_ROWS",
            "MAX_PAYLOAD_BYTES", "CONSISTENCY_LEVEL", "DEGRADE_POLICY", "REVISION_STRATEGY")
        .build();
    CompiledSql compiled = sqlEngine.select(query, "lowcode");
    JSONArray rows = sqlExecutor.query("lowcode", compiled);
    for (int i = 0; i < rows.size(); i++) {
      JSONObject row = rows.getJSONObject(i);
      CacheConfigRow cacheConfigRow = new CacheConfigRow(
          row.getString("CACHE_NAME"),
          row.getIntValue("TTL"),
          row.getIntValue("MAX_CAPACITY"),
          row.getBooleanValue("ENABLE_LOCAL_CACHE"),
          row.getBooleanValue("ENABLE_NULL_CACHE"),
          row.getBooleanValue("ENABLE_PROTECTION"),
          row.getIntValue("BATCH_SIZE"),
          row.getBooleanValue("RANDOM_TTL"),
          row.getIntValue("MAX_RELATION_DEPTH"),
          row.getIntValue("NULL_TTL"),
          row.getBooleanValue("ENABLE_SWR"),
          row.getIntValue("SWR_REFRESH_TTL"),
          row.getBooleanValue("ENABLE_CIRCUIT_BREAKER"),
          row.getIntValue("CB_FAILURE_THRESHOLD"),
          row.getIntValue("CB_RECOVERY_MS"),
          row.getIntValue("ADMISSION_THRESHOLD"),
          row.getIntValue("MAX_BATCH_IDS"),
          row.getIntValue("MAX_RESULT_ROWS"),
          row.getIntValue("MAX_PAYLOAD_BYTES"),
          row.getString("CONSISTENCY_LEVEL"),
          row.getString("DEGRADE_POLICY"),
          row.getString("REVISION_STRATEGY"));
      result.cacheConfigs.put(cacheConfigRow.cacheName, cacheConfigRow);
    }
  }
}
