package com.server.basedata.event;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.SqlEngine;
import com.server.sqlengine.executor.SqlExecutor;
import com.server.sqlengine.model.CompiledSql;
import com.server.sqlengine.model.Condition;
import com.server.sqlengine.model.SelectQuery;
import com.server.sqlengine.model.UpdateStatement;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Outbox 投递器。
 * <p>
 * 基于 Outbox 模式实现缓存事件的可靠投递：业务侧将事件写入 cache_event_outbox 表（状态 PENDING），
 * 本投递器定期扫描待发送事件，逐条加载并通过 RocketMQ 同步发送，发送成功更新状态为 SENT，
 * 失败更新为 FAILED。通过 Outbox 表保证事件不丢失，与业务事务解耦。
 */
@Slf4j
public class OutboxPublisher {

  /** RocketMQ 主题 */
  private static final String TOPIC = "base-data-cache-event";

  /** SQL 引擎 */
  private final SqlEngine sqlEngine;
  /** SQL 执行器 */
  private final SqlExecutor sqlExecutor;
  /** RocketMQ 模板 */
  private final RocketMQTemplate rocketMQTemplate;

  /**
   * 构造投递器。
   *
   * @param sqlEngine        SQL 引擎
   * @param sqlExecutor      SQL 执行器
   * @param rocketMQTemplate RocketMQ 模板
   */
  public OutboxPublisher(SqlEngine sqlEngine, SqlExecutor sqlExecutor,
      RocketMQTemplate rocketMQTemplate) {
    this.sqlEngine = sqlEngine;
    this.sqlExecutor = sqlExecutor;
    this.rocketMQTemplate = rocketMQTemplate;
  }

  /**
   * 投递一批待发送事件。
   * <p>
   * 查询 PENDING 且未超过重试上限的事件，逐条加载→发送→更新状态。
   *
   * @param batchSize 本批最大事件数
   * @return 成功投递的事件数
   */
  public int publishPendingEvents(int batchSize) {
    int publishedCount = 0;
    List<Long> eventIds = queryPendingEvents(batchSize);
    for (Long eventId : eventIds) {
      try {
        CacheEvent event = loadEvent(eventId);
        if (event != null) {
          // 同步发送，确保投递成功后再更新状态
          rocketMQTemplate.syncSend(TOPIC, MessageBuilder.withPayload(event).build());
          updateEventStatus(eventId, "SENT", null);
          publishedCount++;
        }
      } catch (Exception e) {
        // 投递失败，记录失败状态与错误信息
        updateEventStatus(eventId, "FAILED", e.getMessage());
      }
    }
    return publishedCount;
  }

  /**
   * 查询待发送事件 ID 列表。
   * <p>
   * 仅查询状态为 PENDING 且重试次数未超上限的事件，按创建时间升序取前 batchSize 条。
   *
   * @param batchSize 最大查询数
   * @return 事件 ID 列表
   */
  private List<Long> queryPendingEvents(int batchSize) {
    List<Long> result = new ArrayList<Long>();
    // 构建 SELECT 查询：状态为 PENDING 且重试次数未超上限，按创建时间升序取前 batchSize 条
    SelectQuery query = SelectQuery.builder()
        .from("CACHE_EVENT_OUTBOX")
        .column("EVENT_ID")
        .where(Condition.and(
            Condition.eq("STATUS", "PENDING"),
            Condition.raw("RETRY_COUNT < MAX_RETRY")))
        .orderByAsc("CREATE_TIME")
        .limit(batchSize)
        .build();
    CompiledSql compiled = sqlEngine.select(query, "lowcode");
    try {
      JSONArray rows = sqlExecutor.query("lowcode", compiled);
      for (int i = 0; i < rows.size(); i++) {
        JSONObject row = rows.getJSONObject(i);
        result.add(row.getLong("EVENT_ID"));
      }
    } catch (SQLException e) {
      log.error("查询待发送事件失败", e);
    }
    return result;
  }

  /**
   * 加载指定事件的完整内容。
   * <p>
   * ids 列以 JSON 数组存储，使用 fastjson2 解析为 List。
   *
   * @param eventId 事件 ID
   * @return 缓存事件；不存在或加载异常返回 null
   */
  private CacheEvent loadEvent(Long eventId) {
    CacheEvent result = null;
    // 构建 SELECT 查询：按事件 ID 加载完整事件内容
    SelectQuery query = SelectQuery.builder()
        .from("CACHE_EVENT_OUTBOX")
        .columns("EVENT_TYPE", "CACHE_NAME", "SCHEMA_VERSION", "IDS")
        .where(Condition.eq("EVENT_ID", eventId))
        .build();
    CompiledSql compiled = sqlEngine.select(query, "lowcode");
    try {
      JSONArray rows = sqlExecutor.query("lowcode", compiled);
      if (!rows.isEmpty()) {
        JSONObject row = rows.getJSONObject(0);
        String eventType = row.getString("EVENT_TYPE");
        String cacheName = row.getString("CACHE_NAME");
        int schemaVersion = row.getIntValue("SCHEMA_VERSION");
        String idsJson = row.getString("IDS");
        List<Object> ids = null;
        if (idsJson != null && !idsJson.isEmpty()) {
          // ids 以 JSON 数组存储，解析为列表
          ids = JSONArray.parseArray(idsJson, Object.class);
        }
        result = new CacheEvent(eventType, cacheName, schemaVersion, ids);
      }
    } catch (SQLException e) {
      log.error("加载事件失败: eventId={}", eventId, e);
    }
    return result;
  }

  /**
   * 更新事件状态。
   *
   * @param eventId      事件 ID
   * @param status       新状态（SENT/FAILED）
   * @param errorMessage 错误信息，可为 null
   */
  private void updateEventStatus(Long eventId, String status, String errorMessage) {
    // 构建 UPDATE 语句：更新状态与更新时间，按事件 ID 定位
    UpdateStatement stmt = UpdateStatement.builder()
        .table("CACHE_EVENT_OUTBOX")
        .set("STATUS", status)
        .setExpr("UPDATE_TIME", "CURRENT_TIMESTAMP")
        .where(Condition.eq("EVENT_ID", eventId))
        .build();
    CompiledSql compiled = sqlEngine.update(stmt, "lowcode");
    try {
      sqlExecutor.update("lowcode", compiled);
    } catch (SQLException e) {
      log.error("更新事件状态失败: eventId={}, status={}", eventId, status, e);
    }
  }

  /**
   * 获取 RocketMQ 主题。
   *
   * @return 主题
   */
  public String getTopic() {
    return TOPIC;
  }
}
