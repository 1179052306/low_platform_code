package com.server.basedata.event;

import com.server.basedata.cache.KeyBuilder;
import com.server.basedata.cache.L1CaffeineCache;
import com.server.basedata.cache.L2RedisCache;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 缓存失效事件监听器。
 * <p>
 * 消费 RocketMQ 中的 {@link CacheEvent}，按事件类型执行本地 L1 与 L2 缓存失效：
 * <ul>
 * <li>Schema 变更事件：清空所有 L1 缓存（结构变化，旧数据全部失效）</li>
 * <li>缓存失效事件：按主键批量删除 L2 与对应 cacheName 的 L1 中对应 key</li>
 * </ul>
 * 通过监听跨实例广播事件，保证多实例缓存一致性。
 */
@Component
@RocketMQMessageListener(topic = "base-data-cache-event", consumerGroup = "${rocketmq.consumer.group:base-data-cache-consumer}")
public class CacheInvalidationListener implements RocketMQListener<CacheEvent> {

  /** L2 Redis 缓存 */
  @Resource
  private L2RedisCache l2RedisCache;

  /** 各 cacheName 对应的 L1 本地缓存（由 BaseDataConfig 注册为 Map Bean） */
  @Resource
  private Map<String, L1CaffeineCache> l1Caches;

  /** Redis Key 构建器 */
  @Resource
  private KeyBuilder keyBuilder;

  /**
   * 处理收到的缓存事件。
   * <p>
   * Schema 变更事件走全量清空，其余走按主键失效。
   *
   * @param event 缓存事件
   */
  @Override
  public void onMessage(CacheEvent event) {
    if (event == null) {
      return;
    }
    if (event.isSchemaChanged()) {
      handleSchemaChanged(event);
    } else {
      handleCacheInvalidate(event);
    }
  }

  /**
   * 处理缓存失效事件：按主键批量删除 L2 与对应 cacheName 的 L1 中对应 key。
   *
   * @param event 缓存事件
   */
  private void handleCacheInvalidate(CacheEvent event) {
    List<Object> ids = event.getIds();
    if (ids == null || ids.isEmpty()) {
      return;
    }
    int schemaVersion = 0;
    if (event.getSchemaVersion() != null) {
      schemaVersion = event.getSchemaVersion().intValue();
    }
    // 构建待失效的完整 key 列表
    List<String> keys = new ArrayList<String>(ids.size());
    for (Object id : ids) {
      String key = keyBuilder.buildKey(event.getCacheName(), schemaVersion, id);
      keys.add(key);
    }
    // 先删 L2，再删 L1，避免删 L1 后又被 L2 回填
    l2RedisCache.mdelete(keys);
    // 按 cacheName 查找对应的 L1 缓存
    L1CaffeineCache l1 = l1Caches.get(event.getCacheName());
    if (l1 != null) {
      for (String key : keys) {
        l1.invalidate(key);
      }
    }
  }

  /**
   * 处理 Schema 变更事件：清空所有 L1 缓存。
   * <p>
   * 结构变化后旧格式数据全部失效，所有 cacheName 的 L1 直接全量清空；
   * L2 由版本切换时的清理流程处理。
   *
   * @param event 缓存事件
   */
  private void handleSchemaChanged(CacheEvent event) {
    // 遍历所有 cacheName 的 L1 缓存，逐一清空
    for (L1CaffeineCache l1 : l1Caches.values()) {
      l1.invalidateAll();
    }
  }
}
