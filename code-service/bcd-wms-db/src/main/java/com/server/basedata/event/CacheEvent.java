package com.server.basedata.event;

import java.io.Serializable;
import java.util.List;

/**
 * 缓存事件。
 * <p>
 * 跨进程传播的缓存失效与 Schema 变更事件载体，通过 Outbox 表持久化后由 RocketMQ 投递，
 * 各实例消费后执行本地 L1 与 L2 失效。实现 Serializable 以支持 RocketMQ 序列化传输。
 */
public class CacheEvent implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 事件类型：单条缓存失效 */
  public static final String TYPE_CACHE_INVALIDATE = "CACHE_INVALIDATE";
  /** 事件类型：批量缓存失效 */
  public static final String TYPE_CACHE_INVALIDATE_BATCH = "CACHE_INVALIDATE_BATCH";
  /** 事件类型：Schema 变更 */
  public static final String TYPE_SCHEMA_CHANGED = "SCHEMA_CHANGED";

  /** 事件类型 */
  private String eventType;
  /** 缓存空间名 */
  private String cacheName;
  /** 结构版本号 */
  private Integer schemaVersion;
  /** 涉及的主键列表 */
  private List<Object> ids;

  /** 无参构造，供反序列化使用 */
  public CacheEvent() {
  }

  /**
   * 构造缓存事件。
   *
   * @param eventType     事件类型
   * @param cacheName     缓存空间名
   * @param schemaVersion 结构版本号
   * @param ids           主键列表
   */
  public CacheEvent(String eventType, String cacheName, Integer schemaVersion, List<Object> ids) {
    this.eventType = eventType;
    this.cacheName = cacheName;
    this.schemaVersion = schemaVersion;
    this.ids = ids;
  }

  /**
   * 获取事件类型。
   *
   * @return 事件类型
   */
  public String getEventType() {
    return eventType;
  }

  /**
   * 设置事件类型。
   *
   * @param eventType 事件类型
   */
  public void setEventType(String eventType) {
    this.eventType = eventType;
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
   * 设置缓存空间名。
   *
   * @param cacheName 缓存空间名
   */
  public void setCacheName(String cacheName) {
    this.cacheName = cacheName;
  }

  /**
   * 获取结构版本号。
   *
   * @return 结构版本号
   */
  public Integer getSchemaVersion() {
    return schemaVersion;
  }

  /**
   * 设置结构版本号。
   *
   * @param schemaVersion 结构版本号
   */
  public void setSchemaVersion(Integer schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  /**
   * 获取主键列表。
   *
   * @return 主键列表
   */
  public List<Object> getIds() {
    return ids;
  }

  /**
   * 设置主键列表。
   *
   * @param ids 主键列表
   */
  public void setIds(List<Object> ids) {
    this.ids = ids;
  }

  /**
   * 判断是否为批量失效事件。
   *
   * @return 是批量失效返回 true
   */
  public boolean isBatchInvalidate() {
    return TYPE_CACHE_INVALIDATE_BATCH.equals(eventType);
  }

  /**
   * 判断是否为 Schema 变更事件。
   *
   * @return 是 Schema 变更返回 true
   */
  public boolean isSchemaChanged() {
    return TYPE_SCHEMA_CHANGED.equals(eventType);
  }
}
