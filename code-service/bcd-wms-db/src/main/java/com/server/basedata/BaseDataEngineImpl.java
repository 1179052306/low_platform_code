package com.server.basedata;

import com.server.basedata.api.BaseDataEngine;
import com.server.basedata.api.CacheMetrics;
import com.server.basedata.api.CompactRow;
import com.server.basedata.api.ConsistencyLevel;
import com.server.basedata.cache.KeyBuilder;
import com.server.basedata.cache.L1CaffeineCache;
import com.server.basedata.cache.L2RedisCache;
import com.server.basedata.codec.CompactRowCodec;
import com.server.basedata.database.BatchQueryStrategy;
import com.server.basedata.metadata.CompiledEntity;
import com.server.basedata.metadata.CompiledProjection;
import com.server.basedata.metadata.RuntimeMetadata;
import com.server.basedata.metadata.VersionManager;
import com.server.basedata.projection.ProjectionResolver;
import com.server.basedata.protection.BreakdownGuard;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * BaseDataEngine 核心实现 — 串联 L1(Caffeine) → L2(Redis) → DB(PostgreSQL) 查询链路。
 * <p>
 * 查询流程：
 * 1. 从 VersionManager 获取当前 RuntimeMetadata
 * 2. L1 Caffeine 查询 → 命中直接返回
 * 3. L2 Redis MGET 批量查询 → 命中回填 L1
 * 4. DB BatchQueryStrategy 批量查询（BreakdownGuard 保护）→ 回填 L2 + L1
 * 5. Projection 裁剪
 * 6. CacheMetrics 记录指标
 * </p>
 */
@Slf4j
public class BaseDataEngineImpl implements BaseDataEngine {

  private final VersionManager versionManager;
  private final KeyBuilder keyBuilder;
  private final L2RedisCache l2RedisCache;
  private final CompactRowCodec codec;
  private final BatchQueryStrategy batchQueryStrategy;
  private final ProjectionResolver projectionResolver;

  /** 每个 cacheName 对应的 L1 本地缓存 */
  private final Map<String, L1CaffeineCache> l1Caches;

  /** 每个 cacheName 对应的防护组件 */
  private final Map<String, BreakdownGuard> guards;

  /** 每个 cacheName 对应的指标收集器 */
  private final Map<String, CacheMetrics> metricsMap;

  public BaseDataEngineImpl(VersionManager versionManager,
      KeyBuilder keyBuilder,
      L2RedisCache l2RedisCache,
      CompactRowCodec codec,
      BatchQueryStrategy batchQueryStrategy,
      ProjectionResolver projectionResolver,
      Map<String, L1CaffeineCache> l1Caches,
      Map<String, BreakdownGuard> guards,
      Map<String, CacheMetrics> metricsMap) {
    this.versionManager = versionManager;
    this.keyBuilder = keyBuilder;
    this.l2RedisCache = l2RedisCache;
    this.codec = codec;
    this.batchQueryStrategy = batchQueryStrategy;
    this.projectionResolver = projectionResolver;
    this.l1Caches = l1Caches;
    this.guards = guards;
    this.metricsMap = metricsMap;
  }

  @Override
  public CompactRow get(String cacheName, Object id) {
    return get(cacheName, id, null, ConsistencyLevel.EVENTUAL);
  }

  @Override
  public CompactRow get(String cacheName, Object id, String projection) {
    return get(cacheName, id, projection, ConsistencyLevel.EVENTUAL);
  }

  @Override
  public CompactRow get(String cacheName, Object id, String projection,
      ConsistencyLevel consistency) {
    Map<Object, CompactRow> result = getBatchInternal(cacheName,
        singletonList(id), projection, consistency);
    return result.get(id);
  }

  @Override
  public Map<Object, CompactRow> getBatch(String cacheName, Collection<?> ids) {
    return getBatch(cacheName, ids, null);
  }

  @Override
  public Map<Object, CompactRow> getBatch(String cacheName, Collection<?> ids,
      String projection) {
    return getBatchInternal(cacheName, ids, projection, ConsistencyLevel.EVENTUAL);
  }

  /**
   * 批量查询核心逻辑：L1 → L2 → DB → 回填
   */
  private Map<Object, CompactRow> getBatchInternal(String cacheName,
      Collection<?> ids, String projection,
      ConsistencyLevel consistency) {

    CacheMetrics metrics = getOrCreateMetrics(cacheName);
    RuntimeMetadata metadata = versionManager.getCurrent();
    Map<Object, CompactRow> result = new HashMap<Object, CompactRow>();

    if (metadata == null) {
      return result;
    }

    CompiledEntity entity = metadata.getEntity(cacheName);
    if (entity == null) {
      return result;
    }

    // ID 去重
    Set<Object> dedupedIds = new HashSet<Object>(ids);
    if (dedupedIds.isEmpty()) {
      return result;
    }

    // 构建 Key 列表
    int schemaVersion = entity.getSchemaVersion();
    List<String> keys = new ArrayList<String>(dedupedIds.size());
    Map<String, Object> keyToId = new HashMap<String, Object>();
    for (Object id : dedupedIds) {
      String key = keyBuilder.buildKey(cacheName, schemaVersion, id);
      keys.add(key);
      keyToId.put(key, id);
    }

    // Step 1: L1 Caffeine 查询
    List<Object> l1Results = new ArrayList<Object>();
    List<String> l1MissKeys = new ArrayList<String>();
    L1CaffeineCache l1 = l1Caches.get(cacheName);
    for (String key : keys) {
      Object cached;
      if (l1 == null) {
        cached = null;
      } else {
        cached = l1.get(key);
      }
      if (cached != null) {
        l1Results.add(cached);
        metrics.recordL1Hit();
      } else {
        l1MissKeys.add(key);
        metrics.recordL1Miss();
      }
    }

    // 将 L1 命中的结果放入 result
    for (Object obj : l1Results) {
      CompactRow row = decodeFromCache(obj, entity);
      if (row != null) {
        result.put(row.getId(), row);
      }
    }

    if (l1MissKeys.isEmpty()) {
      return applyProjection(result, projection, metadata, cacheName);
    }

    // Step 2: L2 Redis MGET 查询
    List<String> redisValues = l2RedisCache.mget(l1MissKeys);
    List<String> dbMissKeys = new ArrayList<String>();
    Map<String, String> l2Hits = new HashMap<String, String>();

    for (int i = 0; i < l1MissKeys.size(); i++) {
      String key = l1MissKeys.get(i);
      String value = redisValues.get(i);
      if (value != null) {
        l2Hits.put(key, value);
        metrics.recordL2Hit();
      } else {
        dbMissKeys.add(key);
        metrics.recordL2Miss();
      }
    }

    // 解码 L2 命中的结果，回填 L1
    for (Map.Entry<String, String> entry : l2Hits.entrySet()) {
      CompactRow row = codec.decode(entry.getValue(), entity);
      if (row != null) {
        result.put(row.getId(), row);
        if (l1 != null) {
          l1.put(entry.getKey(), entry.getValue());
        }
      }
    }

    if (dbMissKeys.isEmpty()) {
      return applyProjection(result, projection, metadata, cacheName);
    }

    // Step 3: DB 批量查询（BreakdownGuard 熔断保护）
    List<Object> dbIds = new ArrayList<Object>();
    for (String key : dbMissKeys) {
      dbIds.add(keyToId.get(key));
    }

    // 熔断器前置检查：熔断中直接返回已缓存的部分结果
    BreakdownGuard guard = guards.get(cacheName);
    if (guard != null && !guard.getCircuitBreaker().allowRequest()) {
      log.warn("熔断器开启，跳过 DB 查询: cacheName={}", cacheName);
      return applyProjection(result, projection, metadata, cacheName);
    }

    Map<Object, Object[]> dbResults;
    try {
      long dbStart = System.nanoTime();
      dbResults = batchQueryStrategy.batchQuery(
          entity.getTableName(), entity.getIdColumn(),
          entity.getFieldNames(), dbIds);
      long dbDurationMs = (System.nanoTime() - dbStart) / 1000000L;
      // 记录熔断成功与 DB 加载耗时
      if (guard != null) {
        guard.getCircuitBreaker().recordSuccess();
      }
      metrics.recordDbLoad(dbDurationMs);
    } catch (Exception e) {
      log.error("DB 批量查询失败，降级返回已缓存结果: cacheName={}, table={}",
          cacheName, entity.getTableName(), e);
      // 记录熔断失败
      if (guard != null) {
        guard.getCircuitBreaker().recordFailure();
      }
      return applyProjection(result, projection, metadata, cacheName);
    }

    // 将 DB 结果转为 CompactRow，回填 L2 + L1
    Map<String, String> l2Backfill = new HashMap<String, String>();
    for (Map.Entry<Object, Object[]> entry : dbResults.entrySet()) {
      Object id = entry.getKey();
      Object[] values = entry.getValue();
      CompactRow row = new CompactRow(id, values, schemaVersion, 0);
      result.put(id, row);

      String key = keyBuilder.buildKey(cacheName, schemaVersion, id);
      String json = codec.encode(row, entity);
      l2Backfill.put(key, json);
      if (l1 != null) {
        l1.put(key, json);
      }
    }

    // 批量回填 L2 Redis
    if (!l2Backfill.isEmpty()) {
      int ttl = entity.getTtl();
      l2RedisCache.pipelineSet(l2Backfill, ttl);
    }

    return applyProjection(result, projection, metadata, cacheName);
  }

  /**
   * 应用 Projection 裁剪
   */
  private Map<Object, CompactRow> applyProjection(
      Map<Object, CompactRow> result, String projection,
      RuntimeMetadata metadata, String cacheName) {
    Map<Object, CompactRow> finalResult;
    if (projection == null || projection.isEmpty()) {
      finalResult = result;
    } else {
      CompiledProjection proj = metadata.getProjection(cacheName, projection);
      finalResult = projectionResolver.projectAll(result, proj);
    }
    return finalResult;
  }

  /**
   * 从缓存对象解码为 CompactRow（缓存中存的是 JSON 字符串）
   */
  private CompactRow decodeFromCache(Object cached, CompiledEntity entity) {
    CompactRow result;
    if (cached instanceof String) {
      result = codec.decode((String) cached, entity);
    } else if (cached instanceof CompactRow) {
      result = (CompactRow) cached;
    } else {
      result = null;
    }
    return result;
  }

  @Override
  public void invalidate(String cacheName, Object id) {
    CacheMetrics metrics = getOrCreateMetrics(cacheName);
    RuntimeMetadata metadata = versionManager.getCurrent();
    if (metadata == null) {
      return;
    }
    CompiledEntity entity = metadata.getEntity(cacheName);
    if (entity == null) {
      return;
    }

    String key = keyBuilder.buildKey(cacheName, entity.getSchemaVersion(), id);
    l2RedisCache.delete(key);

    L1CaffeineCache l1 = l1Caches.get(cacheName);
    if (l1 != null) {
      l1.invalidate(key);
    }

    BreakdownGuard guard = guards.get(cacheName);
    if (guard != null) {
      guard.invalidate(key);
    }
    metrics.recordInvalidate();
  }

  @Override
  public void invalidateBatch(String cacheName, Collection<?> ids) {
    for (Object id : ids) {
      invalidate(cacheName, id);
    }
  }

  @Override
  public void invalidateAll(String cacheName) {
    CacheMetrics metrics = getOrCreateMetrics(cacheName);

    // 1. 清空 L1 本地缓存
    L1CaffeineCache l1 = l1Caches.get(cacheName);
    if (l1 != null) {
      l1.invalidateAll();
    }

    // 2. 清空 L2 Redis 缓存（按 cacheName pattern 批量删除）
    String pattern = keyBuilder.buildPatternForCacheName(cacheName);
    try {
      long deleted = l2RedisCache.deleteByPattern(pattern);
      log.info("invalidateAll: cacheName={}, L2 删除 {} 个 key", cacheName, deleted);
    } catch (Exception e) {
      log.error("invalidateAll: L2 删除失败, cacheName={}", cacheName, e);
    }

    // 3. 清空空值缓存（防穿透缓存）
    BreakdownGuard guard = guards.get(cacheName);
    if (guard != null) {
      guard.invalidateAll();
    }

    metrics.recordInvalidate();
  }

  @Override
  public void preWarm(String cacheName) {
    // P1 实现：批量预热
  }

  @Override
  public CacheMetrics getMetrics(String cacheName) {
    return getOrCreateMetrics(cacheName);
  }

  private CacheMetrics getOrCreateMetrics(String cacheName) {
    // 使用 computeIfAbsent 保证线程安全地创建并回写到 Map
    return metricsMap.computeIfAbsent(cacheName, k -> new CacheMetrics());
  }

  // ==================== 以下为 P1 异步 API（暂不支持） ====================

  @Override
  public List<CompactRow> resolveBatch(List<?> sourceData) {
    throw new UnsupportedOperationException("resolveBatch — P1 待实现");
  }

  @Override
  public List<CompactRow> resolveBatch(List<?> sourceData, String projection) {
    throw new UnsupportedOperationException("resolveBatch — P1 待实现");
  }

  @Override
  public CompletableFuture<CompactRow> getAsync(String cacheName, Object id) {
    return CompletableFuture.supplyAsync(() -> get(cacheName, id));
  }

  @Override
  public CompletableFuture<CompactRow> getAsync(String cacheName, Object id,
      String projection) {
    return CompletableFuture.supplyAsync(() -> get(cacheName, id, projection));
  }

  @Override
  public CompletableFuture<Map<Object, CompactRow>> getBatchAsync(
      String cacheName, Collection<?> ids) {
    return CompletableFuture.supplyAsync(() -> getBatch(cacheName, ids));
  }

  @Override
  public CompletableFuture<Map<Object, CompactRow>> getBatchAsync(
      String cacheName, Collection<?> ids, String projection) {
    return CompletableFuture.supplyAsync(() -> getBatch(cacheName, ids, projection));
  }

  @Override
  public CompletableFuture<List<CompactRow>> resolveBatchAsync(
      List<?> sourceData, String projection) {
    throw new UnsupportedOperationException("resolveBatchAsync — P1 待实现");
  }

  private static <T> List<T> singletonList(T item) {
    List<T> list = new ArrayList<T>();
    list.add(item);
    return list;
  }
}