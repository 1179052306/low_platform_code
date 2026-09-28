package com.server.basedata;

import com.server.basedata.cache.KeyBuilder;
import com.server.basedata.cache.L2RedisCache;
import com.server.basedata.codec.CompactRowCodec;
import com.server.basedata.database.BatchQueryStrategy;

import com.server.basedata.metadata.MetadataCompiler;
import com.server.basedata.metadata.MetadataLoader;
import com.server.basedata.metadata.SchemaDiff;
import com.server.basedata.metadata.SchemaHashBuilder;
import com.server.basedata.metadata.SchemaScanner;
import com.server.basedata.metadata.VersionManager;
import com.server.basedata.projection.ProjectionResolver;
import com.server.basedata.version.SchemaVersionManager;
import com.server.basedata.version.VersionCleaner;
import com.server.sqlengine.SqlEngine;
import com.server.sqlengine.dialect.SqlDialectRegistry;
import com.server.sqlengine.executor.SqlExecutor;
import com.server.sqlengine.validator.SqlFieldValidator;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;

/**
 * V3 基础数据引擎 Spring 配置类 — 注入所有组件依赖。
 * <p>
 * 依赖外部 Bean：RedissonClient、SqlEngine、SqlExecutor
 * </p>
 */
@Configuration
public class BaseDataConfig {

  /** Key 构建器：bd:{cacheName}:s{schemaVersion}:{id} */
  @Bean
  public KeyBuilder keyBuilder() {
    return new KeyBuilder();
  }

  /** CompactRow ↔ JSON 编解码器 */
  @Bean
  public CompactRowCodec compactRowCodec() {
    return new CompactRowCodec();
  }

  /** 批量查询策略：IN 分批 1000/批，直接复用 sqlengine，经 SqlFieldValidator 校验 */
  @Bean
  public BatchQueryStrategy batchQueryStrategy(SqlEngine sqlEngine, SqlExecutor sqlExecutor,
      SqlFieldValidator fieldValidator) {
    return new BatchQueryStrategy(sqlEngine, sqlExecutor, fieldValidator);
  }

  /** 元数据加载器：从 PostgreSQL 加载 7 张元数据表 */
  @Bean
  public MetadataLoader metadataLoader(SqlEngine sqlEngine, SqlExecutor sqlExecutor) {
    return new MetadataLoader(sqlEngine, sqlExecutor);
  }

  /** 元数据编译器：RawMetadata → RuntimeMetadata */
  @Bean
  public MetadataCompiler metadataCompiler() {
    return new MetadataCompiler();
  }

  /** 版本管理器：AtomicReference<RuntimeMetadata> 原子切换 */
  @Bean
  public VersionManager versionManager() {
    return new VersionManager();
  }

  /** Schema 版本管理器：发布/回滚 */
  @Bean
  public SchemaVersionManager schemaVersionManager(MetadataLoader loader,
      MetadataCompiler compiler, VersionManager versionManager) {
    return new SchemaVersionManager(loader, compiler, versionManager);
  }

  /** Schema 扫描器：通过方言查系统表扫描列结构 */
  @Bean
  public SchemaScanner schemaScanner(SqlDialectRegistry dialectRegistry, SqlExecutor sqlExecutor) {
    return new SchemaScanner(dialectRegistry, sqlExecutor);
  }

  /** Schema 哈希构建器 */
  @Bean
  public SchemaHashBuilder schemaHashBuilder() {
    return new SchemaHashBuilder();
  }

  /** Schema 差异计算器 */
  @Bean
  public SchemaDiff schemaDiff() {
    return new SchemaDiff();
  }

  /** L2 Redis 缓存：MGET + Pipeline + Lua */
  @Bean
  public L2RedisCache l2RedisCache(RedissonClient redissonClient) {
    return new L2RedisCache(redissonClient);
  }

  /** Projection 裁剪器 */
  @Bean
  public ProjectionResolver projectionResolver() {
    return new ProjectionResolver();
  }

  /** 旧版本清理器：SCAN + UNLINK */
  @Bean
  public VersionCleaner versionCleaner(RedissonClient redissonClient) {
    return new VersionCleaner(redissonClient);
  }

  /** L1 Caffeine 缓存 Map（cacheName → L1CaffeineCache），初始化时填充 */
  @Bean
  public java.util.Map<String, com.server.basedata.cache.L1CaffeineCache> l1Caches() {
    return new ConcurrentHashMap<String, com.server.basedata.cache.L1CaffeineCache>();
  }

  /** 防护组件 Map（cacheName → BreakdownGuard），初始化时填充 */
  @Bean
  public java.util.Map<String, com.server.basedata.protection.BreakdownGuard> guards() {
    return new ConcurrentHashMap<String, com.server.basedata.protection.BreakdownGuard>();
  }

  /** 指标收集器 Map（cacheName → CacheMetrics），初始化时填充 */
  @Bean
  public java.util.Map<String, com.server.basedata.api.CacheMetrics> metricsMap() {
    return new ConcurrentHashMap<String, com.server.basedata.api.CacheMetrics>();
  }

  /** BaseDataEngine 核心实现 */
  @Bean
  public BaseDataEngineImpl baseDataEngine(
      VersionManager versionManager,
      KeyBuilder keyBuilder,
      L2RedisCache l2RedisCache,
      CompactRowCodec codec,
      BatchQueryStrategy batchQueryStrategy,
      ProjectionResolver projectionResolver,
      java.util.Map<String, com.server.basedata.cache.L1CaffeineCache> l1Caches,
      java.util.Map<String, com.server.basedata.protection.BreakdownGuard> guards,
      java.util.Map<String, com.server.basedata.api.CacheMetrics> metricsMap) {
    return new BaseDataEngineImpl(versionManager, keyBuilder, l2RedisCache,
        codec, batchQueryStrategy, projectionResolver,
        l1Caches, guards, metricsMap);
  }

  /** 关联解析器：按层批量加载 N:1 关联实体 */
  @Bean
  public com.server.basedata.relation.RelationResolver relationResolver(BaseDataEngineImpl engine) {
    return new com.server.basedata.relation.RelationResolver(engine);
  }
}