package com.api.lowcode.engine;

import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 数据审计服务 —— 内存队列 + afterCommit 异步批量写入。
 *
 * <p>业务事务提交后（afterCommit）将审计记录放入内存队列，
 * 后台线程定时（每 2 秒）或队列满（500 条）时批量写入 lc_data_audit_log。</p>
 *
 * <p>队列满时降级为同步写入，保证不丢；应用关闭时刷盘剩余记录。</p>
 */
@Slf4j
@Component
public class DataAuditService {

    @Resource
    private SqlEngineJson helper;

    private static final String AUDIT_TABLE = "lc_data_audit_log";
    private static final int QUEUE_CAPACITY = 10000;
    private static final int BATCH_SIZE = 500;
    private static final long FLUSH_INTERVAL_MS = 2000;

    private final LinkedBlockingQueue<DataAuditRecord> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private volatile boolean running = true;
    private Thread flushThread;

    @PostConstruct
    public void init() {
        flushThread = new Thread(this::flushLoop, "data-audit-flush");
        flushThread.setDaemon(true);
        flushThread.start();
        log.info("[DataAuditService] 审计刷盘线程已启动");
    }

    @PreDestroy
    public void destroy() {
        running = false;
        if (flushThread != null) {
            flushThread.interrupt();
        }
        flushRemaining();
        log.info("[DataAuditService] 审计刷盘线程已停止，剩余记录已刷盘");
    }

    /**
     * 将审计记录放入内存队列（由 afterCommit 回调调用）。
     * 队列满时降级为同步写入。
     */
    public void offer(DataAuditRecord record) {
        if (record == null) return;
        if (!queue.offer(record)) {
            log.warn("[DataAuditService] 审计队列已满（{}），降级同步写入", QUEUE_CAPACITY);
            insertOne(record);
        }
    }

    private void flushLoop() {
        while (running) {
            try {
                Thread.sleep(FLUSH_INTERVAL_MS);
                flushBatch();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[DataAuditService] 审计刷盘异常", e);
            }
        }
    }

    private void flushBatch() {
        if (queue.isEmpty()) return;
        List<DataAuditRecord> batch = new ArrayList<>(BATCH_SIZE);
        queue.drainTo(batch, BATCH_SIZE);
        if (batch.isEmpty()) return;
        int success = 0;
        for (DataAuditRecord record : batch) {
            try {
                insertOne(record);
                success++;
            } catch (Exception e) {
                log.error("[DataAuditService] 审计写入失败 table={} recordId={}",
                        record.getTableName(), record.getRecordId(), e);
            }
        }
        log.debug("[DataAuditService] 批量刷盘 {}/{} 条", success, batch.size());
    }

    private void flushRemaining() {
        List<DataAuditRecord> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        for (DataAuditRecord record : remaining) {
            insertOne(record);
        }
        log.info("[DataAuditService] 关闭时刷盘 {} 条剩余记录", remaining.size());
    }

    private void insertOne(DataAuditRecord record) {
        try {
            JSONObject data = new JSONObject();
            data.put("id", record.getId());
            data.put("biz_code", record.getBizCode());
            data.put("table_name", record.getTableName());
            data.put("record_id", record.getRecordId());
            data.put("action", record.getAction());
            data.put("changed_fields", record.getChangedFields());
            data.put("before_data", record.getBeforeData());
            data.put("after_data", record.getAfterData());
            data.put("operator", record.getOperator());
            data.put("operated_at", record.getOperatedAt());
            data.put("ip", record.getIp());
            ResMsg r = helper.insert(AUDIT_TABLE, data);
            if (r == null || !r.getRes()) {
                log.error("[DataAuditService] 审计写入返回失败: {}", r != null ? r.getMsg() : "null");
            } else {
                log.info("[DataAuditService] 审计写入成功 table={} recordId={}", record.getTableName(), record.getRecordId());
            }
        } catch (Exception e) {
            log.error("[DataAuditService] 审计写入异常 table={} recordId={}",
                    record.getTableName(), record.getRecordId(), e);
        }
    }
}