package com.server.basedata.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 基础数据引擎核心接口。
 * <p>
 * 面向上层提供基础数据的统一读写入口，屏蔽 L1 本地缓存、L2 Redis 缓存与数据库
 * 的多层访问细节。支持单条/批量/异步获取、投影裁剪、关联批量解析、
 * 一致性级别选择、缓存失效与预热、运行时指标采集等能力。
 * <p>
 * 实现方需保证线程安全，并配合 {@link CacheMetrics} 暴露运行指标。
 */
public interface BaseDataEngine {

        /**
         * 按主键获取单行数据（默认一致性、全字段）。
         *
         * @param cacheName 缓存空间名（通常对应实体名）
         * @param id        主键值
         * @return 紧凑行；不存在时返回 null
         */
        CompactRow get(String cacheName, Object id);

        /**
         * 按主键获取单行数据，并按投影表达式裁剪字段。
         *
         * @param cacheName  缓存空间名
         * @param id         主键值
         * @param projection 投影表达式，指定需要返回的字段
         * @return 裁剪后的紧凑行；不存在时返回 null
         */
        CompactRow get(String cacheName, Object id, String projection);

        /**
         * 批量按主键获取数据（默认一致性、全字段）。
         *
         * @param cacheName 缓存空间名
         * @param ids       主键集合
         * @return 以主键为 key、紧凑行为 value 的映射；不存在的 key 不会出现在结果中
         */
        Map<Object, CompactRow> getBatch(String cacheName, Collection<?> ids);

        /**
         * 批量按主键获取数据，并按投影表达式裁剪字段。
         *
         * @param cacheName  缓存空间名
         * @param ids        主键集合
         * @param projection 投影表达式
         * @return 以主键为 key、裁剪后的紧凑行为 value 的映射
         */
        Map<Object, CompactRow> getBatch(String cacheName, Collection<?> ids, String projection);

        /**
         * 根据源数据批量解析关联行（默认投影）。
         * <p>
         * 源数据中的关联字段值会被收集为主键集合，再批量回源查询关联实体行。
         *
         * @param sourceData 源数据集合，元素可为 CompactRow 或其它携带关联字段的对象
         * @return 解析出的关联行列表
         */
        List<CompactRow> resolveBatch(List<?> sourceData);

        /**
         * 根据源数据批量解析关联行，并按投影裁剪字段。
         *
         * @param sourceData 源数据集合
         * @param projection 投影表达式
         * @return 解析并裁剪后的关联行列表
         */
        List<CompactRow> resolveBatch(List<?> sourceData, String projection);

        /**
         * 异步按主键获取单行数据（默认一致性、全字段）。
         *
         * @param cacheName 缓存空间名
         * @param id        主键值
         * @return 异步返回的紧凑行
         */
        CompletableFuture<CompactRow> getAsync(String cacheName, Object id);

        /**
         * 异步按主键获取单行数据，并按投影裁剪字段。
         *
         * @param cacheName  缓存空间名
         * @param id         主键值
         * @param projection 投影表达式
         * @return 异步返回的裁剪后紧凑行
         */
        CompletableFuture<CompactRow> getAsync(String cacheName, Object id, String projection);

        /**
         * 异步批量按主键获取数据（默认投影）。
         *
         * @param cacheName 缓存空间名
         * @param ids       主键集合
         * @return 异步返回的主键到紧凑行的映射
         */
        CompletableFuture<Map<Object, CompactRow>> getBatchAsync(
                        String cacheName, Collection<?> ids);

        /**
         * 异步批量按主键获取数据，并按投影裁剪字段。
         *
         * @param cacheName  缓存空间名
         * @param ids        主键集合
         * @param projection 投影表达式
         * @return 异步返回的主键到裁剪后紧凑行的映射
         */
        CompletableFuture<Map<Object, CompactRow>> getBatchAsync(
                        String cacheName, Collection<?> ids, String projection);

        /**
         * 异步根据源数据批量解析关联行，并按投影裁剪字段。
         *
         * @param sourceData 源数据集合
         * @param projection 投影表达式
         * @return 异步返回的解析并裁剪后的关联行列表
         */
        CompletableFuture<List<CompactRow>> resolveBatchAsync(
                        List<?> sourceData, String projection);

        /**
         * 按主键获取单行数据，显式指定一致性级别与投影。
         *
         * @param cacheName   缓存空间名
         * @param id          主键值
         * @param projection  投影表达式
         * @param consistency 一致性级别
         * @return 紧凑行；不存在时返回 null
         */
        CompactRow get(String cacheName, Object id, String projection,
                        ConsistencyLevel consistency);

        /**
         * 失效指定主键的缓存（L1 + L2）。
         *
         * @param cacheName 缓存空间名
         * @param id        主键值
         */
        void invalidate(String cacheName, Object id);

        /**
         * 批量失效指定主键集合的缓存（L1 + L2）。
         *
         * @param cacheName 缓存空间名
         * @param ids       主键集合
         */
        void invalidateBatch(String cacheName, Collection<?> ids);

        /**
         * 失效指定缓存空间的全部缓存条目（L1 + L2 + 空值缓存）。
         *
         * @param cacheName 缓存空间名
         */
        void invalidateAll(String cacheName);

        /**
         * 预热指定缓存空间：批量加载热点数据到 L1/L2。
         *
         * @param cacheName 缓存空间名
         */
        void preWarm(String cacheName);

        /**
         * 获取指定缓存空间的运行指标快照。
         *
         * @param cacheName 缓存空间名
         * @return 指标采集器快照
         */
        CacheMetrics getMetrics(String cacheName);
}
