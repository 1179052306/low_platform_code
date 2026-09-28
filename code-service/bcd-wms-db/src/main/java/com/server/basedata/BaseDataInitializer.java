package com.server.basedata;

import com.server.basedata.api.CacheMetrics;
import com.server.basedata.cache.CacheAdmission;
import com.server.basedata.cache.L1CaffeineCache;
import com.server.basedata.metadata.CompiledEntity;
import com.server.basedata.metadata.MetadataCompiler;
import com.server.basedata.metadata.MetadataLoader;
import com.server.basedata.metadata.RuntimeMetadata;
import com.server.basedata.metadata.VersionManager;
import com.server.basedata.protection.BreakdownGuard;
import com.server.basedata.protection.CircuitBreaker;
import com.server.basedata.protection.NullCache;
import com.server.basedata.protection.SingleFlight;
import com.server.basedata.protection.TtlJitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Map;

/**
 * V3 引擎初始化器 — 启动时加载元数据并创建各 cacheName 的 L1/Guard/Metrics。
 * <p>
 * 初始化流程：
 * 1. MetadataLoader 从 PostgreSQL 加载 7 张元数据表
 * 2. MetadataCompiler 预编译为 RuntimeMetadata
 * 3. VersionManager.swap() 原子切换
 * 4. 遍历所有 CompiledEntity，创建 L1CaffeineCache + BreakdownGuard + CacheMetrics
 * </p>
 */
@Slf4j
@Component
public class BaseDataInitializer {

  @Resource
  private MetadataLoader metadataLoader;

  @Resource
  private MetadataCompiler metadataCompiler;

  @Resource
  private VersionManager versionManager;

  @Resource
  private Map<String, L1CaffeineCache> l1Caches;

  @Resource
  private Map<String, BreakdownGuard> guards;

  @Resource
  private Map<String, CacheMetrics> metricsMap;

  /**
   * 启动时自动加载元数据并初始化缓存组件。
   * 如果元数据表为空（未配置），跳过初始化不报错。
   */
  @PostConstruct
  public void init() {
    try {
      log.info("[BaseData] 开始加载元数据...");
      MetadataLoader.RawMetadata raw = metadataLoader.load();

      if (raw.entities.isEmpty()) {
        log.warn("[BaseData] 元数据表为空，跳过初始化。请先在 base_data_entity 等表中配置元数据。");
        return;
      }

      RuntimeMetadata metadata = metadataCompiler.compile(raw, 1);
      versionManager.swap(metadata);

      log.info("[BaseData] 元数据加载完成：{} 个实体", metadata.getEntityCount());

      for (String cacheName : metadata.getAllCacheNames()) {
        CompiledEntity entity = metadata.getEntity(cacheName);
        initL1Cache(cacheName, entity);
        initGuard(cacheName);
        initMetrics(cacheName);
      }

      log.info("[BaseData] 初始化完成：{} 个 L1 缓存, {} 个防护组件",
          l1Caches.size(), guards.size());

    } catch (Exception e) {
      log.error("[BaseData] 初始化失败: {}", e.getMessage(), e);
    }
  }

  /**
   * 为指定 cacheName 创建 L1 Caffeine 本地缓存
   */
  private void initL1Cache(String cacheName, CompiledEntity entity) {
    long maxSize = entity.getMaxCapacity();
    long ttlMs = entity.getTtl() * 1000L;
    CacheAdmission admission = new CacheAdmission(3);
    L1CaffeineCache l1 = new L1CaffeineCache(maxSize, ttlMs, admission);
    l1Caches.put(cacheName, l1);
  }

  /**
   * 为指定 cacheName 创建防护组件（SingleFlight + CircuitBreaker + NullCache + TtlJitter）
   */
  private void initGuard(String cacheName) {
    SingleFlight<com.server.basedata.api.CompactRow> singleFlight = new SingleFlight<com.server.basedata.api.CompactRow>();
    CircuitBreaker circuitBreaker = new CircuitBreaker(10, 30000L);
    NullCache nullCache = new NullCache();
    TtlJitter ttlJitter = new TtlJitter(0.1);
    BreakdownGuard guard = new BreakdownGuard(singleFlight, circuitBreaker,
        nullCache, ttlJitter);
    guards.put(cacheName, guard);
  }

  /**
   * 为指定 cacheName 创建指标收集器
   */
  private void initMetrics(String cacheName) {
    metricsMap.put(cacheName, new CacheMetrics());
  }
}