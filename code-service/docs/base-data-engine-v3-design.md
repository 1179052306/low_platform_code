# WMS BaseData Engine V3 — 生产级技术设计

> **定位**：Metadata-Driven High Performance Base Data Engine，面向 WMS 10M 级基础资料的高性能数据关联运行时。
>
> **技术约束**：JDK 8、Oracle、Redis、Caffeine、Spring Boot/Spring Cloud、MQ。
> **环境**：8 CPU / 32 GB / ~20 微服务 / ~1000 万基础数据。
> **核心原则**：少对象、少序列化、少网络、少 SQL、少锁、少 GC、批量化、预编译、版本化。
> **优先级**：正确性 > 稳定性 > 可观测性 > 性能 > 极限优化。

---

# 一、Oracle DDL（7 张元数据表）

## 1.1 BASE_DATA_ENTITY

```sql
CREATE TABLE BASE_DATA_ENTITY (
    CACHE_NAME        VARCHAR2(64)   NOT NULL,
    ENTITY_NAME       VARCHAR2(128)  NOT NULL,
    TABLE_NAME        VARCHAR2(128)  NOT NULL,
    ID_COLUMN         VARCHAR2(64)   NOT NULL,
    CODE_COLUMN       VARCHAR2(64),
    CACHE_TYPE        VARCHAR2(32)   DEFAULT 'REDIS' NOT NULL,
    TTL               NUMBER(10)     DEFAULT 86400  NOT NULL,
    MAX_CAPACITY      NUMBER(10)     DEFAULT 100000 NOT NULL,
    ENABLED           NUMBER(1)      DEFAULT 1      NOT NULL,
    VERSION           NUMBER(10)     DEFAULT 1      NOT NULL,
    SCHEMA_VERSION    NUMBER(10)     DEFAULT 1      NOT NULL,
    SCHEMA_HASH       VARCHAR2(64),
    UPDATE_TIME       TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_BASE_DATA_ENTITY PRIMARY KEY (CACHE_NAME)
);
```

## 1.2 BASE_DATA_FIELD

```sql
CREATE TABLE BASE_DATA_FIELD (
    CACHE_NAME        VARCHAR2(64)   NOT NULL,
    COLUMN_NAME       VARCHAR2(64)   NOT NULL,
    DATA_TYPE         VARCHAR2(32)   NOT NULL,
    DATA_LENGTH       NUMBER(10),
    DATA_PRECISION    NUMBER(10),
    DATA_SCALE        NUMBER(10),
    NULLABLE          NUMBER(1)      DEFAULT 1 NOT NULL,
    COLUMN_INDEX      NUMBER(10)     NOT NULL,
    ENABLED           NUMBER(1)      DEFAULT 1 NOT NULL,
    FIELD_VERSION     NUMBER(10)     DEFAULT 1 NOT NULL,
    IS_ENCRYPTED      NUMBER(1)      DEFAULT 0 NOT NULL,
    FIELD_TTL         NUMBER(10),
    CONSTRAINT PK_BASE_DATA_FIELD PRIMARY KEY (CACHE_NAME, COLUMN_NAME)
);
COMMENT ON COLUMN BASE_DATA_FIELD.IS_ENCRYPTED IS '1=敏感字段加密存储';
COMMENT ON COLUMN BASE_DATA_FIELD.FIELD_TTL    IS '字段级 TTL（空=用实体默认 TTL）';
```

## 1.3 BASE_DATA_RELATION

```sql
CREATE TABLE BASE_DATA_RELATION (
    SOURCE_CACHE      VARCHAR2(64)   NOT NULL,
    SOURCE_COLUMN     VARCHAR2(64)   NOT NULL,
    TARGET_CACHE      VARCHAR2(64)   NOT NULL,
    TARGET_COLUMN     VARCHAR2(64)   NOT NULL,
    RELATION_TYPE     VARCHAR2(32)   DEFAULT 'MANY_TO_ONE' NOT NULL,
    ENABLED           NUMBER(1)      DEFAULT 1 NOT NULL,
    CONSTRAINT PK_BASE_DATA_RELATION PRIMARY KEY (SOURCE_CACHE, SOURCE_COLUMN, TARGET_CACHE)
);
```

## 1.4 BASE_DATA_PROJECTION

```sql
CREATE TABLE BASE_DATA_PROJECTION (
    CACHE_NAME        VARCHAR2(64)   NOT NULL,
    PROJECTION_NAME   VARCHAR2(32)   NOT NULL,
    FIELDS            CLOB           NOT NULL,
    ENABLED           NUMBER(1)      DEFAULT 1 NOT NULL,
    CONSTRAINT PK_BASE_DATA_PROJECTION PRIMARY KEY (CACHE_NAME, PROJECTION_NAME)
);
```

## 1.5 BASE_DATA_CACHE

```sql
CREATE TABLE BASE_DATA_CACHE (
    CACHE_NAME            VARCHAR2(64)   NOT NULL,
    CACHE_TYPE            VARCHAR2(32)   DEFAULT 'REDIS'     NOT NULL,
    TTL                   NUMBER(10)     DEFAULT 86400        NOT NULL,
    MAX_CAPACITY          NUMBER(10)     DEFAULT 100000       NOT NULL,
    ENABLE_LOCAL_CACHE    NUMBER(1)      DEFAULT 1            NOT NULL,
    ENABLE_NULL_CACHE     NUMBER(1)      DEFAULT 1            NOT NULL,
    ENABLE_PREWARM        NUMBER(1)      DEFAULT 0            NOT NULL,
    ENABLE_PROTECTION     NUMBER(1)      DEFAULT 1            NOT NULL,
    BATCH_SIZE            NUMBER(10)     DEFAULT 1000         NOT NULL,
    RANDOM_TTL            NUMBER(1)      DEFAULT 1            NOT NULL,
    MAX_RELATION_DEPTH    NUMBER(2)      DEFAULT 5            NOT NULL,
    NULL_TTL              NUMBER(10)     DEFAULT 60           NOT NULL,
    LOCK_TIMEOUT_MS       NUMBER(10)     DEFAULT 3000         NOT NULL,
    ENABLE_SWR            NUMBER(1)      DEFAULT 1            NOT NULL,
    SWR_REFRESH_TTL       NUMBER(10)     DEFAULT 7200         NOT NULL,
    ENABLE_CIRCUIT_BREAKER NUMBER(1)     DEFAULT 1            NOT NULL,
    CB_FAILURE_THRESHOLD  NUMBER(5)      DEFAULT 10           NOT NULL,
    CB_RECOVERY_MS        NUMBER(10)     DEFAULT 30000        NOT NULL,
    ADMISSION_THRESHOLD   NUMBER(5)      DEFAULT 3            NOT NULL,
    CONSTRAINT PK_BASE_DATA_CACHE PRIMARY KEY (CACHE_NAME)
);
```

## 1.6 BASE_DATA_SCHEMA_CHANGE

```sql
CREATE TABLE BASE_DATA_SCHEMA_CHANGE (
    CHANGE_ID         NUMBER(20)     NOT NULL,
    CACHE_NAME        VARCHAR2(64)   NOT NULL,
    OLD_VERSION       NUMBER(10),
    NEW_VERSION       NUMBER(10)     NOT NULL,
    CHANGE_TYPE       VARCHAR2(32)   NOT NULL,
    CHANGE_DETAIL     CLOB,
    SCHEMA_HASH_OLD   VARCHAR2(64),
    SCHEMA_HASH_NEW   VARCHAR2(64),
    CREATE_TIME       TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_BASE_DATA_SCHEMA_CHANGE PRIMARY KEY (CHANGE_ID)
);
```

## 1.7 CACHE_EVENT_OUTBOX

```sql
CREATE TABLE CACHE_EVENT_OUTBOX (
    EVENT_ID          NUMBER(20)     NOT NULL,
    EVENT_TYPE        VARCHAR2(64)   NOT NULL,
    CACHE_NAME        VARCHAR2(64),
    SCHEMA_VERSION    NUMBER(10),
    DATA_VERSION      NUMBER(10),
    IDS               CLOB,
    STATUS            VARCHAR2(16)   DEFAULT 'PENDING' NOT NULL,
    RETRY_COUNT       NUMBER(5)      DEFAULT 0         NOT NULL,
    MAX_RETRY         NUMBER(5)      DEFAULT 3         NOT NULL,
    CREATE_TIME       TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATE_TIME       TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_CACHE_EVENT_OUTBOX PRIMARY KEY (EVENT_ID)
);
CREATE INDEX IDX_OUTBOX_STATUS ON CACHE_EVENT_OUTBOX (STATUS, CREATE_TIME);
```

## 1.8 序列

```sql
CREATE SEQUENCE SEQ_SCHEMA_CHANGE START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_OUTBOX_EVENT  START WITH 1 INCREMENT BY 1 NOCACHE;
```

---

# 二、Redis Key 规范与 Value 编码

## 2.1 Key 格式

```
bd:{tenant}:{cacheName}:s{schemaVersion}:d{dataVersion}:{id}
```

- `bd` — Base Data 命名空间
- `{tenant}` — 租户标识（单租户默认 `t0`）
- `{cacheName}` — 实体缓存名
- `s{schemaVersion}` — Schema 版本（字段增删改 +1）
- `d{dataVersion}` — Data 版本（大批量变更 +1）
- `{id}` — 主键值

## 2.2 Value 编码（双模式）

**JSON 模式（默认，易调试）**：

```json
{
  "ITEM_ID": 10001,
  "ITEM_CODE": "A001",
  "ITEM_NAME": "矿泉水",
  "SPEC": "500ml",
  "UNIT_ID": 10,
  "OWNER_ID": 200,
  "_v": 25
}
```

- `_v` — 数据版本号，用于 SWR 版本校验

**MessagePack 二进制模式（P2，省 40-60% 网络/CPU）**：

```
0x92  // array header (2 elements)
  0x19  // version: 25
  0x86  // map header (6 key-value pairs)
    ... 字段名索引 + 值 ...
```

空值标记：`__NULL__`

## 2.3 Redis 操作策略

| 操作           | 方式              | 说明                     |
| -------------- | ----------------- | ------------------------ |
| 批量读取       | **MGET**          | 一次网络往返取 N 个 Key  |
| 批量回填       | **Pipeline SET**  | N 次 SET 合并为 1 次 RTT |
| 读取+判断+回填 | **Lua 脚本**      | 原子执行，1 次 RTT       |
| 旧版本清理     | **SCAN + UNLINK** | 禁止 KEYS                |
| 热点 Key 续期  | **PEXPIRE**       | SWR 后台刷新时续期       |

## 2.4 Lua 脚本：读取+判断+回填原子操作

```lua
-- KEYS[1] = redisKey
-- ARGV[1] = ttlMs
-- 返回: [hitType, value]  hitType: 1=L1命中, 2=Redis命中, 3=miss
local val = redis.call('GET', KEYS[1])
if val then
    return {2, val}
end
return {3, ''}
```

---

# 三、核心接口定义

## 3.1 BaseDataEngine — 同步 + 异步 API

```java
package com.server.basedata.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface BaseDataEngine {

    // ==================== 同步 API ====================

    CompactRow get(String cacheName, Object id);

    CompactRow get(String cacheName, Object id, String projection);

    Map<Object, CompactRow> getBatch(String cacheName, Collection<?> ids);

    Map<Object, CompactRow> getBatch(String cacheName, Collection<?> ids, String projection);

    List<CompactRow> resolveBatch(List<?> sourceData);

    List<CompactRow> resolveBatch(List<?> sourceData, String projection);

    // ==================== 异步 API（P1） ====================

    CompletableFuture<CompactRow> getAsync(String cacheName, Object id);

    CompletableFuture<CompactRow> getAsync(String cacheName, Object id, String projection);

    CompletableFuture<Map<Object, CompactRow>> getBatchAsync(
            String cacheName, Collection<?> ids);

    CompletableFuture<Map<Object, CompactRow>> getBatchAsync(
            String cacheName, Collection<?> ids, String projection);

    CompletableFuture<List<CompactRow>> resolveBatchAsync(
            List<?> sourceData, String projection);

    // ==================== 一致性控制 ====================

    CompactRow get(String cacheName, Object id, String projection,
            ConsistencyLevel consistency);

    // ==================== 缓存管理 ====================

    void invalidate(String cacheName, Object id);

    void invalidateBatch(String cacheName, Collection<?> ids);

    void preWarm(String cacheName);

    // ==================== 监控 ====================

    CacheMetrics getMetrics(String cacheName);
}
```

## 3.2 ConsistencyLevel

```java
package com.server.basedata.api;

public enum ConsistencyLevel {
    EVENTUAL,        // 允许读旧值（默认，SWR 生效）
    STRONGER,        // 优先读最新（失效后短暂 bypass cache）
    BYPASS_CACHE     // 直接查 Oracle
}
```

## 3.3 CompactRow — 紧凑行（三种存储模式）

```java
package com.server.basedata.api;

/**
 * 紧凑行数据结构 — 禁止 Map<String,Object>。
 *
 * 三种存储模式：
 * - OBJECT_ARRAY: Object[]（P0，通用）
 * - PRIMITIVE:    long[]/int[]/double[] + Object[]（P2，省装箱）
 * - BINARY:       byte[] + 字段偏移（P2，省对象）
 */
public final class CompactRow {

    private final Object id;
    private final int schemaVersion;
    private final int dataVersion;

    // OBJECT_ARRAY 模式
    private final Object[] values;

    // PRIMITIVE 模式（P2）
    private final long[]   longValues;
    private final int[]    intValues;
    private final double[] doubleValues;

    // BINARY 模式（P2）
    private final byte[]   binaryData;
    private final int[]    fieldOffsets;

    // 构造器省略，按模式选择

    public Object get(int index) {
        if (values != null) return values[index];
        if (binaryData != null) return readBinary(index);
        // PRIMITIVE 模式按字段类型路由
        return null;
    }

    public Object getId()          { return id; }
    public int  getSchemaVersion() { return schemaVersion; }
    public int  getDataVersion()   { return dataVersion; }
    public int  size()             { return values != null ? values.length : fieldOffsets.length; }

    private Object readBinary(int index) {
        int offset = fieldOffsets[index];
        int nextOffset = index + 1 < fieldOffsets.length ? fieldOffsets[index + 1] : binaryData.length;
        return new String(binaryData, offset, nextOffset - offset); // 简化
    }
}
```

## 3.4 RuntimeMetadata + 预编译类

```java
package com.server.basedata.metadata;

public final class RuntimeMetadata {
    private final long version;
    private final CompiledEntity[] entities;
    private final CompiledRelation[] relations;
    private final CompiledProjection[] projections;
    private final FieldDictionary fieldDictionary; // P2: 字段字典编码

    // getEntity / getRelations / getProjection 省略
}

public final class CompiledEntity {
    private final String cacheName;
    private final String tableName;
    private final String idColumn;
    private final String codeColumn;
    private final String[] fieldNames;
    private final Map<String, Integer> fieldToIndex;
    private final int schemaVersion;
    private final String schemaHash;
    private final boolean[] encryptedFlags;  // 字段加密标记
    private final int[] fieldTtls;           // 字段级 TTL（P2 多级 TTL）

    public int getFieldIndex(String fieldName) { return fieldToIndex.get(fieldName); }
}

public final class CompiledProjection {
    private final String cacheName;
    private final String projectionName;
    private final int[] fieldIndexes;  // 预编译字段 index 数组
}

public final class CompiledRelation {
    private final String sourceCache;
    private final int sourceFieldIndex;
    private final String targetCache;
    private final String targetColumn;
    private final int depth;
}

/**
 * 字段字典编码（P2）— 重复字符串值用整数 ID 替代。
 * 如 100 个仓库名 "北京仓" 只存 1 次，引用处存字典 ID=5。
 */
public final class FieldDictionary {
    private final Map<String, Map<Object, Integer>> dictionary; // cacheName+field → value→id
    private final Map<String, List<Object>> reverse;             // cacheName+field → id→value

    public Integer encode(String cacheName, String field, Object value) { ... }
    public Object decode(String cacheName, String field, int id) { ... }
}
```

---

# 四、13 个模块类设计

## 4.1 api

```
api/
├── BaseDataEngine.java       — 同步+异步 API（见 3.1）
├── CompactRow.java           — 紧凑行（见 3.3）
├── ConsistencyLevel.java     — 一致性级别
├── BaseDataResult.java       — 查询结果包装（含来源标记+耗时）
└── CacheMetrics.java         — 指标快照
```

## 4.2 planner — 查询规划 + 自适应策略

```
planner/
├── QueryPlanner.java
├── QueryPlan.java
├── BatchStrategySelector.java  — 固定阈值（P0）
└── AdaptiveStrategySelector.java — 自适应阈值（P2，根据命中率动态调整）
```

```java
public class QueryPlanner {
    private final BatchStrategySelector strategySelector;

    public QueryPlan plan(String cacheName, Collection<?> ids,
            String projection, int maxRelationDepth) {
        BatchStrategy strategy = strategySelector.selectStrategy(ids.size());
        int batchSize = strategySelector.selectBatchSize(ids.size());
        return new QueryPlan(cacheName, ids, projection, strategy, batchSize, maxRelationDepth);
    }
}

public class AdaptiveStrategySelector extends BatchStrategySelector {
    private volatile int smallThreshold  = 100;
    private volatile int mediumThreshold = 2000;

    /** 根据历史命中率动态调整阈值（P2） */
    public void adjust(int currentHitRate, long avgLatencyMs) {
        if (avgLatencyMs > 50 && smallThreshold > 50) {
            smallThreshold = Math.max(50, smallThreshold - 10);
        }
    }
}

public final class QueryPlan {
    public enum BatchStrategy {
        SINGLE,         // 单条
        REDIS_MGET,     // <=100
        REDIS_ORACLE,   // 100~2000
        ORACLE_GTT      // >2000
    }
}
```

## 4.3 batch — 批量引擎 + ID 去重 + BatchContext 复用

```
batch/
├── BatchEngine.java        — 批量执行引擎
├── BatchContext.java       — 可复用缓冲区
├── IdDeduplicator.java     — ID 归一化 + 去重
├── IdSet.java              — ID 集合接口
├── LongIdSet.java          — Long 专用（P2，避免 HashSet<Long> 装箱）
└── StringIdSet.java        — String 专用
```

```java
public class BatchContext {
    private final List<Object> inputRows;
    private final Set<Object> deduplicatedIds;
    private final Map<Object, CompactRow> l1Hits;
    private final Map<Object, CompactRow> redisHits;
    private final List<Object> dbMisses;
    private final Map<Object, CompactRow> resolvedRows;
    private final Set<Object> loadedEntities;  // 关联去重：已加载的实体

    public BatchContext(int estimatedSize) { ... }
    public void reset() { ... }  // 复用，清空所有缓冲区
}

public class IdDeduplicator {
    /** 归一化 + 去重：Oracle NUMBER → Long, VARCHAR → String */
    public Set<Object> deduplicate(Collection<?> ids, CompiledEntity entity) { ... }
}
```

## 4.4 metadata — 元数据加载 + 预编译 + Schema 扫描

```
metadata/
├── MetadataLoader.java        — 从 Oracle 加载 7 张表
├── MetadataCompiler.java      — 预编译为 RuntimeMetadata
├── RuntimeMetadata.java       — 运行时元数据（见 3.4）
├── CompiledEntity.java        — 预编译实体
├── CompiledProjection.java    — 预编译投影
├── CompiledRelation.java      — 预编译关联
├── FieldDictionary.java       — 字段字典编码（P2）
├── SchemaScanner.java         — 扫描 USER_TAB_COLUMNS
├── SchemaDiff.java            — Schema 差异计算
├── SchemaHashBuilder.java     — Schema 哈希
└── VersionManager.java        — AtomicReference 原子切换
```

```java
public class VersionManager {
    private final AtomicReference<RuntimeMetadata> current = new AtomicReference<>();

    public RuntimeMetadata getCurrent() { return current.get(); }
    public void swap(RuntimeMetadata newMetadata) { current.set(newMetadata); }
    public boolean compareAndSwap(RuntimeMetadata expected, RuntimeMetadata update) {
        return current.compareAndSet(expected, update);
    }
}

public class SchemaScanner {
    /** 扫描 Oracle USER_TAB_COLUMNS */
    public List<ColumnInfo> scanTable(String tableName) { ... }
}

public class SchemaHashBuilder {
    /** 基于 COLUMN_NAME + DATA_TYPE + DATA_LENGTH + DATA_PRECISION + DATA_SCALE + NULLABLE */
    public String buildHash(List<ColumnInfo> columns) { ... }
}
```

## 4.5 cache — 多级缓存 + 准入 + SWR

```
cache/
├── L0RequestCache.java         — 请求级去重
├── L1CaffeineCache.java        — 本地热点
├── L2RedisCache.java           — Redis 共享（MGET + Pipeline + Lua）
├── KeyBuilder.java             — Key 构建
├── CacheAdmission.java         — 准入策略（TinyLFU + 访问计数）
├── StaleWhileRevalidate.java   — SWR：过期返回旧值 + 后台刷新
└── RedisLuaScripts.java        — Lua 脚本常量
```

```java
public class L1CaffeineCache {
    private final Cache<String, Object> cache;
    private final CacheAdmission admission;

    public L1CaffeineCache(long maxSize, long ttlMs, CacheAdmission admission) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttlMs, TimeUnit.MILLISECONDS)
                .recordStats()
                .build();
        this.admission = admission;
    }

    public Object get(String key) { return cache.getIfPresent(key); }

    /** 准入控制：低频数据不进 L1 */
    public void put(String key, Object value) {
        if (admission.shouldAdmit(key)) {
            cache.put(key, value);
        }
    }
}

public class L2RedisCache {
    private final RedisService redisService;
    private final RedissonClient redissonClient;

    /** MGET 批量读取 */
    public List<String> mget(List<String> keys) {
        return redisService.multiGet(keys);
    }

    /** Pipeline 批量写入（P1：N 次 SET → 1 次 RTT） */
    public void pipelineSet(Map<String, String> kvMap, long ttlMs) {
        RBatch batch = redissonClient.createBatch();
        for (Map.Entry<String, String> entry : kvMap.entrySet()) {
            batch.getBucket(entry.getKey()).setAsync(entry.getValue(), ttlMs, TimeUnit.MILLISECONDS);
        }
        batch.execute();
    }

    /** Lua 原子读取+判断（P1） */
    public Object luaGetOrMiss(String key, String luaScript) { ... }
}

public class CacheAdmission {
    private final int threshold;  // 访问次数阈值

    /** 只有访问次数 >= threshold 才晋升 Caffeine */
    public boolean shouldAdmit(String key) {
        int count = accessCounter.getCount(key);
        return count >= threshold;
    }
}

public class StaleWhileRevalidate {
    private final L1CaffeineCache l1;
    private final L2RedisCache l2;
    private final ExecutorService refreshExecutor;

    /**
     * SWR 读取：
     * 1. 缓存未过期 → 直接返回
     * 2. 缓存已过期但 SWR 期内 → 返回旧值 + 后台异步刷新
     * 3. 超过 SWR 期 → 同步刷新
     */
    public CompactRow get(String key, Supplier<CompactRow> loader,
            long ttlMs, long swrTtlMs) {
        Object cached = l1.get(key);
        if (cached != null) {
            // 检查是否在 SWR 期
            if (isWithinSwrWindow(key, ttlMs, swrTtlMs)) {
                refreshExecutor.submit(() -> {
                    CompactRow fresh = loader.get();
                    l1.put(key, fresh);
                    l2.set(key, encode(fresh), ttlMs);
                });
            }
            return (CompactRow) cached;
        }
        // 缓存不存在，同步加载
        return loader.get();
    }
}
```

## 4.6 codec — JSON + MessagePack + Binary

```
codec/
├── JsonCodec.java          — JSON 编解码（P0 默认）
├── MessagePackCodec.java   — MessagePack 二进制（P2）
├── CompactRowCodec.java    — CompactRow ↔ JSON/MessagePack
└── FieldEncryptor.java     — 字段级加解密（P2）
```

```java
public interface Codec {
    String encode(CompactRow row, CompiledEntity entity);
    CompactRow decode(String data, CompiledEntity entity);
    byte[] encodeBinary(CompactRow row, CompiledEntity entity);
    CompactRow decodeBinary(byte[] data, CompiledEntity entity);
}

public class CompactRowCodec implements Codec {
    // JSON 模式
    public String encode(CompactRow row, CompiledEntity entity) { ... }
    public CompactRow decode(String json, CompiledEntity entity) { ... }

    // MessagePack 二进制模式（P2）
    public byte[] encodeBinary(CompactRow row, CompiledEntity entity) { ... }
    public CompactRow decodeBinary(byte[] data, CompiledEntity entity) { ... }
}

public class FieldEncryptor {
    /** 加密敏感字段（P2） */
    public Object encrypt(Object value, String algorithm) { ... }
    public Object decrypt(Object encrypted, String algorithm) { ... }
}
```

## 4.7 relation — 关联图 + 并行解析 + 循环检测

```
relation/
├── RelationGraph.java         — 关联图
├── RelationResolver.java      — 串行关联解析（P0）
├── ParallelRelationResolver.java — 并行关联解析（P1，同层 CompletableFuture 并行）
├── CycleDetector.java         — 循环检测
└── CompiledRelation.java      — 预编译关联
```

```java
public class ParallelRelationResolver {
    private final BaseDataEngine engine;
    private final ExecutorService executor;
    private final int maxDepth;

    /**
     * 并行关联解析（P1）：
     * 同层多个关联 CompletableFuture 并行加载。
     * ITEM → OWNER + ITEM → CATEGORY 同时执行，延迟减半。
     */
    public void resolve(List<CompactRow> sourceData,
            CompiledRelation[] relations,
            CompiledEntity entity,
            RuntimeMetadata metadata) {

        Set<String> visited = new ConcurrentHashSet<>();
        visited.add(entity.getCacheName());

        for (int depth = 0; depth < maxDepth; depth++) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (CompiledRelation rel : relations) {
                if (visited.contains(rel.getTargetCache())) continue;
                visited.add(rel.getTargetCache());

                futures.add(CompletableFuture.runAsync(() -> {
                    Set<Object> targetIds = extractIds(sourceData, rel);
                    if (!targetIds.isEmpty()) {
                        Map<Object, CompactRow> targetRows =
                            engine.getBatch(rel.getTargetCache(), targetIds);
                        // 递归下一层
                        CompiledRelation[] next = metadata.getRelations(rel.getTargetCache());
                        if (next.length > 0) {
                            resolve(new ArrayList<>(targetRows.values()), next,
                                metadata.getEntity(rel.getTargetCache()), metadata);
                        }
                    }
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            if (futures.isEmpty()) break;
        }
    }
}
```

## 4.8 projection — 预编译投影

```
projection/
├── ProjectionCompiler.java — 投影预编译（字段名 → index 数组）
└── ProjectionResolver.java — 按 index 裁剪
```

```java
public class ProjectionResolver {
    /** 按 index 数组裁剪 */
    public CompactRow project(CompactRow row, CompiledProjection projection) {
        int[] indexes = projection.getFieldIndexes();
        Object[] projected = new Object[indexes.length];
        for (int i = 0; i < indexes.length; i++) {
            projected[i] = row.get(indexes[i]);
        }
        return new CompactRow(row.getId(), projected, row.getSchemaVersion(), row.getDataVersion());
    }
}
```

## 4.9 database — Oracle + 批量 + GTT + PS 缓存

```
database/
├── OracleRepository.java          — Oracle 数据访问
├── BatchQueryStrategy.java        — IN 分批（1000/批）
├── GttStrategy.java               — Oracle GTT（>2000 ID）
└── PreparedStatementCache.java    — PS 缓存复用（P1）
```

```java
public class BatchQueryStrategy {
    private static final int ORACLE_IN_LIMIT = 1000;

    /** IN 分批查询 */
    public Map<Object, Object[]> batchQuery(String tableName, String idColumn,
            String[] fieldNames, Collection<?> ids) {
        Map<Object, Object[]> result = new HashMap<>();
        List<Object> idList = new ArrayList<>(ids);
        for (int offset = 0; offset < idList.size(); offset += ORACLE_IN_LIMIT) {
            int end = Math.min(offset + ORACLE_IN_LIMIT, idList.size());
            result.putAll(repository.queryByIds(tableName, idColumn, fieldNames,
                idList.subList(offset, end)));
        }
        return result;
    }
}

public class GttStrategy {
    /**
     * Oracle 全局临时表策略（P2）：
     * >2000 ID 时，先 INSERT INTO GTT，再 JOIN 查询。
     * 执行计划更优，避免超长 IN 列表。
     */
    public Map<Object, Object[]> gttQuery(String tableName, String idColumn,
            String[] fieldNames, Collection<?> ids) {
        // 1. INSERT INTO BASE_DATA_GTT (ID) VALUES (?), ... 批量插入
        // 2. SELECT t.* FROM tableName t JOIN BASE_DATA_GTT g ON t.idColumn = g.ID
        // 3. TRUNCATE TABLE BASE_DATA_GTT（会话级 GTT 自动清理）
    }
}

public class PreparedStatementCache {
    /** 缓存 PreparedStatement，按 SQL 文本复用（P1） */
    private final ConcurrentHashMap<String, PreparedStatement> cache;
    public PreparedStatement getOrPrepare(Connection conn, String sql) { ... }
}
```

## 4.10 protection — 全套故障保护

```
protection/
├── SingleFlight.java             — 防击穿（合并并发回源）
├── NullCache.java                — 防穿透（空值短 TTL）
├── TtlJitter.java                — 防雪崩（TTL ±10% 随机偏移）
├── CircuitBreaker.java           — 熔断降级
├── StaleWhileRevalidate.java     — SWR（见 4.5）
└── BreakdownGuard.java           — 综合防护入口
```

```java
public class SingleFlight<T> {
    private final ConcurrentHashMap<String, CompletableFuture<T>> flights = new ConcurrentHashMap<>();

    /** 同一 key 并发只执行一次，其余等待 */
    public T execute(String key, Supplier<T> supplier) { ... }
}

public class CircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }
    private volatile State state = State.CLOSED;
    private final int failureThreshold;
    private final long recoveryTimeoutMs;

    public boolean allowRequest() { ... }
    public void recordSuccess() { ... }
    public void recordFailure() { ... }
}

public class BreakdownGuard {
    private final SingleFlight<CompactRow> singleFlight;
    private final CircuitBreaker circuitBreaker;
    private final NullCache nullCache;
    private final TtlJitter ttlJitter;

    /** 综合防护：熔断 → SingleFlight → 空值缓存 → TTL 随机 */
    public CompactRow protectedLoad(String key, Supplier<CompactRow> loader) {
        if (!circuitBreaker.allowRequest()) {
            return null; // 降级
        }
        try {
            CompactRow result = singleFlight.execute(key, () -> {
                CompactRow row = loader.get();
                if (row == null) nullCache.put(key);
                return row;
            });
            circuitBreaker.recordSuccess();
            return result;
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            throw e;
        }
    }
}
```

## 4.11 version — Schema + Data 版本管理

```
version/
├── SchemaVersionManager.java — Schema 版本（加字段 s3→s4）
├── DataVersionManager.java   — Data 版本（大批量 d101→d102）
└── VersionCleaner.java       — 旧版本 SCAN+UNLINK 清理
```

## 4.12 event — MQ + Outbox

```
event/
├── CacheEvent.java                — 事件对象
├── OutboxPublisher.java           — Outbox → MQ 投递
├── CacheInvalidationListener.java — MQ 失效消费
└── SchemaChangedListener.java     — MQ Schema 变更消费
```

## 4.13 monitor — Micrometer + OpenTelemetry + 告警

```
monitor/
├── CacheMetrics.java           — 指标收集
├── MicrometerExporter.java     — Micrometer → Prometheus（P1）
├── TraceContext.java           — OpenTelemetry 链路追踪（P1）
└── AlertManager.java           — 实时告警（P1）
```

```java
public class CacheMetrics {
    private final AtomicLong l1HitCount     = new AtomicLong();
    private final AtomicLong l1MissCount    = new AtomicLong();
    private final AtomicLong l2HitCount     = new AtomicLong();
    private final AtomicLong l2MissCount    = new AtomicLong();
    private final AtomicLong dbLoadCount    = new AtomicLong();
    private final AtomicLong dbLoadTimeMs   = new AtomicLong();
    private final AtomicLong nullCacheCount = new AtomicLong();
    private final AtomicLong singleFlightCount = new AtomicLong();
    private final AtomicLong invalidateCount = new AtomicLong();
    private final AtomicLong circuitBreakerCount = new AtomicLong();

    // 记录方法 + 计算命中率/平均耗时
}

public class MicrometerExporter {
    private final MeterRegistry meterRegistry;

    /** 暴露到 Prometheus，Grafana 可直接消费 */
    public void export(CacheMetrics metrics, String cacheName) {
        meterRegistry.gauge("basedata.l1.hit.rate",
                Tags.of("cache", cacheName), metrics.getL1HitRate());
        meterRegistry.gauge("basedata.l2.hit.rate",
                Tags.of("cache", cacheName), metrics.getL2HitRate());
        meterRegistry.counter("basedata.db.load.count",
                Tags.of("cache", cacheName)).increment(metrics.getDbLoadCount());
    }
}

public class TraceContext {
    /** OpenTelemetry 链路追踪：L1→L2→DB 全链路耗时 */
    public Span startSpan(String operation, String cacheName, Object id) {
        return tracer.spanBuilder(operation)
                .setAttribute("cache.name", cacheName)
                .setAttribute("cache.id", String.valueOf(id))
                .startSpan();
    }
}

public class AlertManager {
    /** 命中率骤降 / 回源激增 → 告警 */
    public void checkAndAlert(CacheMetrics metrics, String cacheName) {
        if (metrics.getL1HitRate() < 0.3) {
            sendAlert("L1命中率低于30%: " + cacheName);
        }
        if (metrics.getDbLoadCount() > 10000) {
            sendAlert("回源次数超过1万: " + cacheName);
        }
    }
}
```

---

# 五、核心流程设计

## 5.1 批量查询流程（含 Pipeline + Lua + SWR）

```
getBatch("B_ITEM", [10001, 10002, 10003], "PDA")
  │
  ├─ 1. QueryPlanner.plan() → strategy=REDIS_MGET, batchSize=3
  ├─ 2. BatchContext 创建 + IdDeduplicator 去重
  │
  ├─ 3. L1 Caffeine 查询
  │     → l1Hits = {10001 → row1}
  │     → missing = {10002, 10003}
  │
  ├─ 4. L2 Redis MGET（Lua 原子读取）
  │     → redisHits = {10002 → row2}
  │     → dbMisses = {10003}
  │     → 命中的 Pipeline 回填 L1
  │
  ├─ 5. L3 Oracle Batch（SingleFlight + CircuitBreaker 保护）
  │     → SingleFlight.execute("B_ITEM:10003", () -> {
  │         BatchQueryStrategy.batchQuery("WMS_ITEM", "ITEM_ID", fields, [10003])
  │       })
  │     → Pipeline 回填 L2 Redis + L1 Caffeine
  │
  ├─ 6. SWR 检查（P1）
  │     → 对每条结果检查是否在 SWR 期
  │     → 过期但 SWR 期内 → 返回旧值 + 后台异步刷新
  │
  ├─ 7. 合并 + Projection 裁剪
  │     → CompiledProjection("PDA") = [1, 2, 5]
  │     → 按 index 数组裁剪
  │
  └─ 8. TraceContext 记录链路 + CacheMetrics 记录指标
```

## 5.2 并行关联解析流程（P1）

```
resolveBatch(sourceData, "PDA")
  │
  ├─ depth=0: 加载 B_ITEM
  │   → getBatch("B_ITEM", {10001, 10002})
  │   → 得到 OWNER_ID: {200, 201}, CATEGORY_ID: {30, 31}
  │
  ├─ depth=1: 并行加载 B_OWNER + B_CATEGORY
  │   ├─ CompletableFuture.supplyAsync(() → getBatch("B_OWNER", {200, 201}))
  │   └─ CompletableFuture.supplyAsync(() → getBatch("B_CATEGORY", {30, 31}))
  │   → allOf().join()  // 等待同层全部完成
  │
  ├─ depth=2: 检查 B_OWNER/B_CATEGORY 是否有下一层关联
  │   → 无 → 结束
  │
  └─ Projection 裁剪 + 返回
```

## 5.3 缓存失效流程（MQ + Outbox + 版本号）

```
Oracle 数据更新（同一事务）
  │
  ├─ 1. UPDATE WMS_ITEM SET ITEM_NAME='新名称' WHERE ITEM_ID=10001
  ├─ 2. INSERT INTO CACHE_EVENT_OUTBOX (EVENT_TYPE='CACHE_INVALIDATE_BATCH',
  │      CACHE_NAME='B_ITEM', SCHEMA_VERSION=3, IDS='[10001]')
  └─ 3. COMMIT
       │
       ├─ 4. OutboxPublisher → MQ
       └─ 5. CacheInvalidationListener 消费
             → Redis DELETE bd:t0:B_ITEM:s3:d1:10001
             → Caffeine invalidate
             → 下次查询重新加载
```

## 5.4 Schema 升级流程

```
表新增字段 ITEM_WEIGHT
  │
  ├─ 1. SchemaScanner 检测新列 → SchemaHash 变化
  ├─ 2. 事务内: BASE_DATA_FIELD INSERT + BASE_DATA_ENTITY SCHEMA_VERSION=4
  │      + BASE_DATA_SCHEMA_CHANGE INSERT + CACHE_EVENT_OUTBOX INSERT
  ├─ 3. COMMIT → OutboxPublisher → MQ
  ├─ 4. SchemaChangedListener → MetadataLoader 重载 → Compiler 重编译
  │      → VersionManager.swap(newRuntimeMetadata)  // AtomicReference
  ├─ 5. 新请求用 s4 版本 Key: bd:t0:B_ITEM:s4:d1:10001
  └─ 6. VersionCleaner 后台 SCAN+UNLINK 清理 s3 旧 Key
```

## 5.5 异步查询流程（P1）

```
getAsync("B_ITEM", 10001, "PDA")
  │
  └─ CompletableFuture.supplyAsync(() → {
        return get("B_ITEM", 10001, "PDA");  // 复用同步逻辑
     }, ioExecutor)
     .thenApply(row → projectionResolver.project(row, projection))
     .whenComplete((result, ex) → {
        traceContext.endSpan();
        metrics.record();
     });
```

---

# 六、生产级特性汇总

## 6.1 完整特性清单

| 类别         | 特性                                 | 阶段 | 说明                               |
| ------------ | ------------------------------------ | ---- | ---------------------------------- |
| **数据结构** | CompactRow (Object[])                | P0   | 禁止 Map<String,Object>            |
|              | PrimitiveRow (long[]/int[]/double[]) | P2   | 省装箱，减 GC                      |
|              | BinaryRow (byte[] + 偏移)            | P2   | 省 Java 对象                       |
|              | 字段字典编码                         | P2   | 重复字符串→整数 ID                 |
| **通信**     | Redis MGET                           | P0   | 批量读取                           |
|              | Pipeline SET                         | P1   | 批量回填 1 次 RTT                  |
|              | Lua 原子脚本                         | P1   | 读取+判断+回填 1 次 RTT            |
|              | MessagePack 二进制                   | P2   | 省 40-60% 网络/CPU                 |
|              | Redis Cluster hash tag               | P2   | 同实体同 slot                      |
| **查询**     | Oracle IN 分批                       | P0   | 1000/批                            |
|              | Oracle GTT                           | P2   | >2000 ID                           |
|              | PreparedStatement 缓存               | P1   | 省 SQL 解析                        |
|              | 自适应阈值                           | P2   | 动态调整                           |
| **缓存**     | Caffeine 有界                        | P0   | maximumSize + TTL                  |
|              | Cache Admission                      | P1   | 低频不进 L1                        |
|              | SWR                                  | P1   | 过期返回旧值+后台刷新              |
|              | 冷热分离                             | P2   | 热 Caffeine / 温 Redis / 冷 Oracle |
|              | 多级 TTL                             | P2   | 字段级 TTL                         |
| **关联**     | 按层批量加载                         | P0   | 禁止 N+1                           |
|              | 并行关联                             | P1   | 同层 CompletableFuture 并行        |
|              | CycleDetector                        | P0   | 循环检测                           |
|              | 关联去重                             | P1   | BatchContext 已加载去重            |
| **一致性**   | MQ + Outbox                          | P0   | 可靠投递                           |
|              | SchemaVersion                        | P0   | 加字段不删百万 Key                 |
|              | DataVersion                          | P1   | 大批量不删百万 Key                 |
|              | 版本号校验                           | P1   | 缓存带 version 读时校验            |
|              | Read-Through                         | P1   | 缓存层自动加载                     |
| **保护**     | SingleFlight                         | P0   | 防击穿                             |
|              | NullCache                            | P0   | 防穿透                             |
|              | TtlJitter                            | P0   | 防雪崩                             |
|              | CircuitBreaker                       | P1   | 熔断降级                           |
|              | SWR                                  | P1   | 过期不阻塞                         |
| **异步**     | CompletableFuture API                | P1   | 不阻塞工作线程                     |
|              | 批量异步                             | P2   | 多 cacheName 并行                  |
| **监控**     | CacheMetrics                         | P0   | 基础计数                           |
|              | Micrometer + Prometheus              | P1   | Grafana 仪表盘                     |
|              | OpenTelemetry 链路追踪               | P1   | L1→L2→DB 全链路                    |
|              | 实时告警                             | P1   | 命中率骤降/回源激增                |
| **安全**     | 租户隔离                             | P1   | Key 含 tenant                      |
|              | 字段级加密                           | P2   | 敏感字段加密                       |
|              | 权限过滤                             | P2   | Projection 按角色裁剪              |
| **智能化**   | 热点自动发现                         | P2   | 按访问频率自动预热                 |
|              | 访问模式学习                         | P3   | 预测下一批热点                     |

## 6.2 性能预期

| 指标               | P0     | P1               | P2                 |
| ------------------ | ------ | ---------------- | ------------------ |
| 单条查询 P99       | ~2ms   | ~1ms (SWR)       | ~0.5ms (Binary)    |
| 批量 1000 条       | ~10ms  | ~5ms (Pipeline)  | ~3ms (MessagePack) |
| 关联 3 层 10000 条 | ~50ms  | ~25ms (并行)     | ~15ms              |
| L1 命中率          | ~80%   | ~90% (Admission) | ~95% (SWR)         |
| JVM Heap (每实例)  | ~500MB | ~300MB           | ~150MB (Primitive) |
| GC 频率            | 基线   | -30%             | -60% (Binary)      |

---

# 七、分阶段实施计划

## 第一步：建表 + 元数据（P0）

```
7 张 DDL 建表
→ MetadataLoader 从 Oracle 加载
→ MetadataCompiler 预编译
→ VersionManager 驻留 JVM（AtomicReference）
→ SchemaScanner + SchemaHashBuilder
```

## 第二步：核心查询链路（P0）

```
CompactRow + CompactRowCodec（JSON 模式）
→ L1CaffeineCache + L2RedisCache + KeyBuilder
→ OracleRepository + BatchQueryStrategy（IN 分批）
→ BatchEngine + IdDeduplicator
→ BaseDataEngine.get / getBatch
→ ProjectionResolver
```

## 第三步：关联 + 保护 + 失效（P0）

```
RelationGraph + RelationResolver + CycleDetector
→ BaseDataEngine.resolveBatch
→ SingleFlight + NullCache + TtlJitter
→ OutboxPublisher + CacheInvalidationListener
→ SchemaVersion + VersionCleaner
```

## 第四步：P1 生产级增强

```
Pipeline + Lua 脚本
→ ParallelRelationResolver（CompletableFuture 并行）
→ SWR + CacheAdmission
→ CircuitBreaker
→ 异步 API（getAsync / getBatchAsync）
→ Micrometer + OpenTelemetry + AlertManager
→ PreparedStatement 缓存
→ DataVersion + 版本号校验
```

## 第五步：P2 极致优化

```
PrimitiveRow / BinaryRow
→ MessagePack 二进制编解码
→ Oracle GTT 策略
→ 字段字典编码
→ 多级 TTL + 冷热分离
→ 自适应阈值
→ 字段级加密 + 权限过滤
→ 热点自动发现
```

## 第六步：P3（仅 Benchmark 证明必要时）

```
OffHeap / DirectByteBuffer / Zero Copy
→ 访问模式学习
→ 响应式 API
```

---

# 八、关键设计决策汇总

| 决策点     | 选择                            | 理由                         |
| ---------- | ------------------------------- | ---------------------------- |
| 存储结构   | **行存储 CompactRow**           | 列存储在 Redis Hash 内存更大 |
| 字段裁剪   | **Projection（index 数组）**    | 替代列存储                   |
| 失效机制   | **MQ + Oracle Outbox**          | Pub/Sub 无持久化             |
| 版本控制   | **SchemaVersion + DataVersion** | 不删百万 Key                 |
| 元数据     | **7 张 DB 表 + JVM 驻留**       | 动态配置                     |
| 批量策略   | **MGET + IN 分批 + GTT**        | 按数量自适应                 |
| 防击穿     | **SingleFlight**                | 合并并发回源                 |
| 防穿透     | **NullCache**                   | 空值短 TTL                   |
| 防雪崩     | **TTL Jitter**                  | ±10% 随机偏移                |
| 故障降级   | **CircuitBreaker + SWR**        | Redis 故障不全部打 Oracle    |
| 旧版本清理 | **SCAN + UNLINK**               | 禁止 KEYS                    |
| 运行时结构 | **CompactRow**                  | 禁止 Map<String,Object>      |
| 元数据切换 | **AtomicReference**             | 不阻塞请求                   |
| 批量回填   | **Pipeline**                    | N 次 SET → 1 次 RTT          |
| 原子操作   | **Lua 脚本**                    | 读取+判断+回填 1 次 RTT      |
| 并行关联   | **CompletableFuture**           | 同层并行，延迟减半           |
| 过期策略   | **SWR**                         | 旧值先返回 + 后台刷新        |
| 监控       | **Micrometer + OpenTelemetry**  | 业界标准                     |
| 异步       | **CompletableFuture API**       | 不阻塞工作线程               |
| ORM 职责   | **仅 Entity/SQL/CRUD**          | 不负责缓存/关联/投影         |
