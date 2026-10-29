package com.api.lowcode.dataimport.write;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.common.NumberRuleEngine;
import com.api.lowcode.engine.SnowflakeIdGenerator;
import com.api.lowcode.meta.BizMeta;
import com.api.lowcode.meta.FieldMeta;
import com.api.lowcode.tx.ActionContext;
import com.api.lowcode.tx.TransactionOrchestrator;
import com.common.returns.ResMsg;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 主从写入编排：批事务（TransactionOrchestrator.run, timeout 可配）内
 * 主表 batchInsert → 明细行回填外键（父表主键值）→ 各明细表 batchInsert。
 * 主键生成遵循 keyStrategy：SNOWFLAKE→雪花 / UUID→32 位串 / 业务主键→用户值。
 * 编码规则字段留空走 NumberRuleEngine（发号加入批事务）。
 */
@Slf4j
@Component
public class MasterDetailWriter {

    @Resource
    private SqlEngineJson helper;

    @Resource
    private TransactionOrchestrator txOrchestrator;

    @Resource
    private NumberRuleEngine numberRuleEngine;

    @Resource
    private com.api.lowcode.dataimport.ImportProperties properties;

    /** 明细表定义（写入用） */
    public static class DetailTarget {
        public String tableName;
        public String keyExpr;
        public List<String> fields;
    }

    /** 一批写入结果 */
    public static class BatchResult {
        public int successRecords;
        public int failedRecords;
        public String error;
    }

    /**
     * 写入一批主记录（含明细）。返回批结果；批内任一失败整批回滚（错误上抛由流水线记批失败）。
     */
    public BatchResult writeBatch(BizMeta meta, List<DetailTarget> details,
                                  List<com.api.lowcode.dataimport.parse.ParsedDocument.MasterRecord> batch,
                                  String taskId) {
        BatchResult result = new BatchResult();
        try {
            txOrchestrator.run(meta.getBizCode(), "importCreate", new ActionContext(),
                    ctx -> {
                        doWrite(meta, details, batch);
                        return null;
                    }, properties.getSyncTimeoutSeconds());
            result.successRecords = batch.size();
        } catch (Exception e) {
            result.failedRecords = batch.size();
            result.error = e.getMessage();
            log.error("[MasterDetailWriter] 批写入失败（整批回滚）: taskId={}, rows={}, {}",
                    taskId, batch.size(), e.getMessage());
        }
        return result;
    }

    private void doWrite(BizMeta meta, List<DetailTarget> details,
                         List<com.api.lowcode.dataimport.parse.ParsedDocument.MasterRecord> batch) throws Exception {
        String pk = meta.getPrimaryKeyField() != null ? meta.getPrimaryKeyField() : "id";
        boolean systemPk = isSystemPk(meta, pk);

        // 1. 主表行准备：主键生成 + 编码规则默认值 + 时间戳
        JSONArray masterRows = new JSONArray();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        for (com.api.lowcode.dataimport.parse.ParsedDocument.MasterRecord rec : batch) {
            JSONObject row = new JSONObject();
            row.putAll(rec.master);
            applyPrimaryKey(meta, pk, systemPk, row);
            applyCodeRules(meta, row);
            row.put("created_at", now);
            row.put("updated_at", now);
            rec.master.put(pk, row.get(pk));
            masterRows.add(row);
        }

        // 2. 主表分片 batchInsert
        insertShards(meta.getMainTable(), masterRows);

        // 3. 明细行回填外键并写入
        String parentPkType = "SNOWFLAKE".equalsIgnoreCase(meta.getKeyStrategy()) ? "BIGINT" : "VARCHAR(64)";
        for (DetailTarget dt : details) {
            JSONArray detailRows = new JSONArray();
            String detailPk = dt.keyExpr != null && !dt.keyExpr.isEmpty() ? dt.keyExpr : "id";
            for (com.api.lowcode.dataimport.parse.ParsedDocument.MasterRecord rec : batch) {
                List<Map<String, Object>> rows = rec.detailsByTable.get(dt.tableName);
                if (rows == null) continue;
                Object parentPkVal = rec.master.get(pk);
                for (Map<String, Object> detailData : rows) {
                    JSONObject row = new JSONObject();
                    row.putAll(detailData);
                    if (isDetailSystemPk(detailPk)) {
                        // 明细表建表固定 keyExpr 类型 VARCHAR(64)（useSnowflake=false），主键用 UUID 对齐列类型
                        row.put(detailPk, UUID.randomUUID().toString().replace("-", ""));
                    }
                    // 关联外键：父表主键字段（与建表时自动加的外键一致）
                    row.put(pk, parentPkVal);
                    row.put("created_at", now);
                    row.put("updated_at", now);
                    detailRows.add(row);
                }
            }
            if (!detailRows.isEmpty()) {
                insertShards(dt.tableName, detailRows);
            }
        }
    }

    /** 分片 batchInsert（每片 batchInsertShard 行） */
    private void insertShards(String table, JSONArray rows) throws Exception {
        for (int from = 0; from < rows.size(); from += properties.getBatchInsertShard()) {
            int to = Math.min(rows.size(), from + properties.getBatchInsertShard());
            JSONArray shard = new JSONArray();
            for (int i = from; i < to; i++) {
                shard.add(rows.getJSONObject(i));
            }
            ResMsg r = helper.batchInsert(table, shard);
            SqlEngineJson.check(r);
        }
    }

    private void applyPrimaryKey(BizMeta meta, String pk, boolean systemPk, JSONObject row) {
        if (!systemPk) {
            return; // 业务主键：用户填的值（必填校验已保证非空）
        }
        if (row.get(pk) == null || String.valueOf(row.get(pk)).trim().isEmpty()) {
            if ("SNOWFLAKE".equalsIgnoreCase(meta.getKeyStrategy())) {
                row.put(pk, SnowflakeIdGenerator.nextId());
            } else {
                row.put(pk, UUID.randomUUID().toString().replace("-", ""));
            }
        }
    }

    /** 编码规则字段（default_value_expr 非空）且 Excel 留空 → NumberRuleEngine 生成 */
    private void applyCodeRules(BizMeta meta, JSONObject row) {
        if (meta.getFields() == null) return;
        for (FieldMeta f : meta.getFields()) {
            String expr = f.getDefaultValueExpr();
            if (expr == null || expr.trim().isEmpty() || !expr.trim().startsWith("{")) continue;
            Object current = row.get(f.getFieldName());
            if (current == null || String.valueOf(current).trim().isEmpty()) {
                try {
                    String generated = numberRuleEngine.generate(expr, row, meta.getBizCode(), f.getFieldName());
                    if (generated != null && !generated.isEmpty()) {
                        row.put(f.getFieldName(), generated);
                    }
                } catch (Exception e) {
                    log.warn("[MasterDetailWriter] 编码规则生成失败: field={}, {}", f.getFieldName(), e.getMessage());
                }
            }
        }
    }

    /** 主键是否为系统字段（不在业务字段列表中 → 自动生成；与 CrudExecutor.isSystemPk 语义一致） */
    private boolean isSystemPk(BizMeta meta, String pk) {
        if (meta.getFields() == null) return true;
        for (FieldMeta f : meta.getFields()) {
            if (pk.equalsIgnoreCase(f.getFieldName())) return false;
        }
        return true;
    }

    private boolean isDetailSystemPk(String detailPk) {
        return "id".equalsIgnoreCase(detailPk);
    }

    /** 明细字段集合提取（模板列结构同源） */
    public static List<DetailTarget> toDetailTargets(
            List<Map.Entry<String, List<String>>> detailTables, Map<String, String> keyExprByTable) {
        List<DetailTarget> targets = new ArrayList<>();
        for (Map.Entry<String, List<String>> dt : detailTables) {
            DetailTarget t = new DetailTarget();
            t.tableName = dt.getKey();
            t.keyExpr = keyExprByTable.getOrDefault(dt.getKey(), "id");
            t.fields = new ArrayList<>(dt.getValue());
            targets.add(t);
        }
        return targets;
    }
}