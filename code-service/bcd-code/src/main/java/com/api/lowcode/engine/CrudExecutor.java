package com.api.lowcode.engine;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.common.BizException;
import com.api.lowcode.engine.spel.SpelExecutor;
import com.api.lowcode.meta.ActionMeta;
import com.api.lowcode.meta.BizMeta;
import com.api.lowcode.meta.FieldMeta;
import com.api.lowcode.tx.ActionContext;
import com.common.returns.ResMsg;
import com.server.sqlengine.SqlEngineFacade;
import com.server.sqlengine.SqlEngineJson;
import com.server.db.MultiDataSourceHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class CrudExecutor {

    @Resource
    private SqlEngineJson helper;

    @Resource
    private SqlEngineFacade sqlEngineFacade;

    @Resource
    private SpelExecutor spelExecutor;

    @Resource
    private com.api.lowcode.common.NumberRuleEngine numberRuleEngine;

    @Resource(name = "multiDataSource")
    private DataSource dataSource;

    @Resource
    private DataAuditService dataAuditService;

    private static final String DB_NAME = "lowcode";

    /** 前端传入的唯一性校验配置键（元数据，非表字段，入库前必须剔除） */
    private static final String UNIQUE_CHECKS_KEY = "__uniqueChecks";

    /**
     * 唯一性兜底校验：前端配置的唯一性规则随请求传入，在事务内 insert/update 前查库，
     * 防两人同时保存相同值绕过前端校验。每条 check: {field, fields:[...], message}
     * 元数据侧（lc_field_meta.is_unique / unique_group）自动拼装检查项后与之合并去重，
     * 未传 __uniqueChecks 的调用方（网关直调/导入）同样获得服务端防重。
     */
    private void validateUniqueChecks(BizMeta meta, JSONObject data, JSONArray uniqueChecks,
                                      String excludePk, Object excludePkVal) throws Exception {
        JSONArray checks = mergeUniqueChecks(meta, uniqueChecks);
        if (checks == null || checks.isEmpty()) return;
        for (int i = 0; i < checks.size(); i++) {
            JSONObject check = checks.getJSONObject(i);
            if (check == null) continue;
            JSONArray fields = check.getJSONArray("fields");
            if (fields == null || fields.isEmpty()) continue;
            JSONArray conditions = new JSONArray();
            JSONObject connector = new JSONObject();
            connector.put("Symbol", "and");
            conditions.add(connector);
            boolean hasValue = false;
            for (int j = 0; j < fields.size(); j++) {
                String fn = fields.getString(j);
                if (fn == null || fn.isEmpty()) continue;
                Object v = data.get(fn);
                if (v == null) v = data.get(fn.toLowerCase());
                if (v == null) v = data.get(fn.toUpperCase());
                if (v == null || (v instanceof String && ((String) v).isEmpty())) continue;
                JSONObject cond = new JSONObject();
                cond.put("Id", fn);
                cond.put("Symbol", "=");
                cond.put("Val", v);
                conditions.add(cond);
                hasValue = true;
            }
            if (!hasValue) continue;
            if (excludePk != null && excludePkVal != null) {
                JSONObject cond = new JSONObject();
                cond.put("Id", excludePk);
                cond.put("Symbol", "<>");
                cond.put("Val", excludePkVal);
                conditions.add(cond);
            }
            JSONObject countReq = new JSONObject();
            countReq.put("tableName", meta.getMainTable());
            countReq.put("conditions", conditions);
            ResMsg cr = sqlEngineFacade.count(countReq, DB_NAME);
            if (cr.getRes() && cr.getData() instanceof Number && ((Number) cr.getData()).intValue() > 0) {
                String message = check.getString("message");
                String field = check.getString("field");
                String msg = message != null && !message.isEmpty()
                        ? message
                        : "唯一性校验失败: " + (field != null ? field : "?") + " 已存在";
                throw new BizException("UNIQUE_VIOLATION", msg);
            }
        }
    }

    /**
     * 元数据（is_unique / unique_group）自动生成唯一性检查项，与前端传入的 __uniqueChecks
     * 按组键（字段集合排序 join）去重合并 —— 前端已传同组检查时跳过元数据侧重复项。
     */
    private JSONArray mergeUniqueChecks(BizMeta meta, JSONArray fromFrontend) {
        JSONArray merged = new JSONArray();
        Set<String> seenGroupKeys = new HashSet<>();
        if (fromFrontend != null) {
            for (int i = 0; i < fromFrontend.size(); i++) {
                JSONObject check = fromFrontend.getJSONObject(i);
                if (check == null) continue;
                JSONArray fields = check.getJSONArray("fields");
                if (fields != null && !fields.isEmpty()) {
                    seenGroupKeys.add(groupKey(fields));
                }
                merged.add(check);
            }
        }
        if (meta.getFields() != null) {
            for (FieldMeta f : meta.getFields()) {
                if (!Boolean.TRUE.equals(f.getIsUnique())) continue;
                JSONArray fields = new JSONArray();
                String groupJson = f.getUniqueGroup();
                if (groupJson != null && !groupJson.trim().isEmpty()) {
                    try {
                        JSONArray group = JSONArray.parseArray(groupJson);
                        if (group != null && !group.isEmpty()) fields = group;
                    } catch (Exception ignore) {
                        // JSON 解析失败回退单字段检查
                    }
                }
                if (fields.isEmpty()) fields.add(f.getFieldName());
                String key = groupKey(fields);
                if (seenGroupKeys.contains(key)) continue;
                seenGroupKeys.add(key);
                JSONObject check = new JSONObject();
                check.put("field", f.getFieldName());
                check.put("fields", fields);
                check.put("message", "唯一性校验失败: " + f.getFieldName() + " 已存在");
                merged.add(check);
            }
        }
        return merged;
    }

    /** 组键：字段列表小写排序后逗号连接（集合相同即视为同一组检查） */
    private String groupKey(JSONArray fields) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            String fn = fields.getString(i);
            if (fn != null && !fn.isEmpty()) names.add(fn.toLowerCase());
        }
        Collections.sort(names);
        return String.join(",", names);
    }

    public Object execute(BizMeta meta, String action, ActionContext ctx) throws Exception {
        // enable/disable 复用 update 权限：若未单独注册 enable/disable action，则检查 update 是否可用
        if ("enable".equals(action) || "disable".equals(action)) {
            ActionMeta am = meta.getAction(action);
            if (am == null || !am.isEnabled()) {
                ActionMeta updateMeta = meta.getAction("update");
                if (updateMeta == null || !updateMeta.isEnabled()) {
                    throw new IllegalArgumentException("业务 " + meta.getBizCode() + " 不支持操作: " + action);
                }
            }
        } else if ("auditHistory".equals(action)) {
            // 审计历史是只读操作，直接放行
        } else {
            ActionMeta actionMeta = meta.getAction(action);
            if (actionMeta == null || !actionMeta.isEnabled()) {
                throw new IllegalArgumentException("业务 " + meta.getBizCode() + " 不支持操作: " + action);
            }
        }
        switch (action) {
            case "create":
                return doCreate(meta, ctx);
            case "update":
                return doUpdate(meta, ctx);
            case "delete":
                return doDelete(meta, ctx);
            case "query":
                return doQuery(meta, ctx);
            case "enable":
                return doEnable(meta, ctx);
            case "disable":
                return doDisable(meta, ctx);
            case "auditHistory":
                return doAuditHistory(meta, ctx);
            default:
                ActionMeta am = meta.getAction(action);
                if (am != null && am.getStatusTransition() != null && !am.getStatusTransition().isEmpty()) {
                    return doStatusTransition(meta, action, ctx, am);
                }
                throw new IllegalArgumentException("未知的 action: " + action);
        }
    }

    /** 获取主键字段名（fallback id） */
    private String getPk(BizMeta meta) {
        String pk = meta.getPrimaryKeyField();
        return pk != null && !pk.isEmpty() ? pk : "id";
    }

    /** 主键是否为系统字段（非业务字段，需自动生成值） */
    private boolean isSystemPk(BizMeta meta, String pk) {
        if (meta.getFields() == null) return true;
        for (FieldMeta f : meta.getFields()) {
            if (pk.equalsIgnoreCase(f.getFieldName())) return false;
        }
        return true;
    }

    private JSONObject doCreate(BizMeta meta, ActionContext ctx) throws Exception {
        JSONObject payload = ctx.getPayload();
        JSONObject data = new JSONObject(payload);
        JSONArray uniqueChecks = payload.getJSONArray(UNIQUE_CHECKS_KEY);
        data.remove(UNIQUE_CHECKS_KEY);
        String pk = getPk(meta);
        if (isSystemPk(meta, pk) && data.getString(pk) == null) {
            if ("SNOWFLAKE".equalsIgnoreCase(meta.getKeyStrategy())) {
                data.put(pk, SnowflakeIdGenerator.nextId());
            } else {
                data.put(pk, UUID.randomUUID().toString().replace("-", ""));
            }
        }
        applyDefaultValues(meta, data, true, ctx);
        validateFields(meta, data, true);
        filterWritableFields(meta, data, ctx);
        validateUniqueChecks(meta, data, uniqueChecks, null, null);
        ResMsg r = helper.insert(meta.getMainTable(), data);
        SqlEngineJson.check(r);
        JSONObject result = new JSONObject();
        result.put(pk, data.getString(pk));
        log.info("[CrudExecutor] create 成功 bizCode={} table={} {}={}", meta.getBizCode(), meta.getMainTable(),
                pk, data.getString(pk));
        return result;
    }

    private JSONObject doUpdate(BizMeta meta, ActionContext ctx) throws Exception {
        JSONObject payload = ctx.getPayload();
        String pk = getPk(meta);
        String id = payload.getString(pk);
        if (id == null || id.isEmpty()) id = payload.getString(pk.toLowerCase());
        if (id == null || id.isEmpty()) id = payload.getString(pk.toUpperCase());
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("update 操作缺少 " + pk);
        }
        JSONObject data = new JSONObject(payload);
        data.remove(pk);
        data.remove(pk.toLowerCase());
        data.remove(pk.toUpperCase());
        JSONArray uniqueChecks = payload.getJSONArray(UNIQUE_CHECKS_KEY);
        data.remove(UNIQUE_CHECKS_KEY);
        applyDefaultValues(meta, data, false, ctx);
        validateFields(meta, data, false);
        filterWritableFields(meta, data, ctx);
        validateUniqueChecks(meta, data, uniqueChecks, pk, id);

        // 审计：UPDATE 前先 SELECT 旧值（不带 TableAlias，避免无 alias 时 SQL 报错）
        JSONObject beforeRow = null;
        try {
            JSONArray auditConds = new JSONArray();
            JSONObject auditConn = new JSONObject();
            auditConn.put("Symbol", "and");
            auditConds.add(auditConn);
            JSONObject auditCond = new JSONObject();
            auditCond.put("Id", pk);
            auditCond.put("Symbol", "=");
            auditCond.put("Val", id);
            auditConds.add(auditCond);
            JSONArray oldRows = helper.query(meta.getMainTable(), null, auditConds, null, false);
            log.info("[审计] SELECT旧值 table={} pk={} id={} 返回行数={}", meta.getMainTable(), pk, id, oldRows != null ? oldRows.size() : 0);
            if (oldRows != null && !oldRows.isEmpty()) {
                beforeRow = oldRows.getJSONObject(0);
            }
        } catch (Exception e) {
            log.warn("[CrudExecutor] 审计-查询旧值失败（不影响更新）: {}", e.getMessage());
        }

        JSONArray conditions = helper.eq(pk, id);
        conditions = mergeDataScopeConditions(meta, ctx, conditions);
        ResMsg r = helper.update(meta.getMainTable(), data, conditions);
        SqlEngineJson.check(r);

        // 审计：afterCommit 入队
        if (beforeRow != null) {
            recordAuditAfterCommit(meta, ctx, id, beforeRow, data);
        }

        JSONObject result = new JSONObject();
        result.put(pk, id);
        result.put("affectedRows", r.getData());
        log.info("[CrudExecutor] update 成功 bizCode={} table={} {}={}", meta.getBizCode(), meta.getMainTable(), pk, id);
        return result;
    }

    private JSONObject doDelete(BizMeta meta, ActionContext ctx) throws Exception {
        JSONObject payload = ctx.getPayload();
        String pk = getPk(meta);
        String id = payload.getString(pk);
        if (id == null || id.isEmpty()) id = payload.getString(pk.toLowerCase());
        if (id == null || id.isEmpty()) id = payload.getString(pk.toUpperCase());
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("delete 操作缺少 " + pk);
        }
        // 断号补偿：删除前回收编码字段中的流水号（规则含流水号段且 seqGapFill=true 时生效）
        try {
            JSONArray rows = helper.query(meta.getMainTable(), null, helper.eq(pk, id), null, false);
            if (rows != null && !rows.isEmpty()) {
                recycleSequenceNumbers(meta, rows.getJSONObject(0));
            }
        } catch (Exception e) {
            log.warn("[CrudExecutor] 删除前断号回收失败（不影响删除）: {}", e.getMessage());
        }
        String logicDeleteField = meta.getLogicDeleteField();
        JSONArray conditions = helper.eq(pk, id);
        conditions = mergeDataScopeConditions(meta, ctx, conditions);
        if (logicDeleteField != null && !logicDeleteField.isEmpty()) {
            JSONObject data = new JSONObject();
            data.put(logicDeleteField, true);
            ResMsg r = helper.update(meta.getMainTable(), data, conditions);
            SqlEngineJson.check(r);
            log.info("[CrudExecutor] 逻辑删除成功 bizCode={} table={} {}={}", meta.getBizCode(), meta.getMainTable(),
                    pk, id);
        } else {
            ResMsg r = helper.delete(meta.getMainTable(), conditions);
            SqlEngineJson.check(r);
            log.info("[CrudExecutor] 物理删除成功 bizCode={} table={} {}={}", meta.getBizCode(), meta.getMainTable(),
                    pk, id);
        }
        JSONObject result = new JSONObject();
        result.put(pk, id);
        return result;
    }

    /** 启用：将状态字段置为 1，支持批量 ids */
    private JSONObject doEnable(BizMeta meta, ActionContext ctx) throws Exception {
        return doStatusUpdate(meta, ctx, "enable", 1);
    }

    /** 禁用：将状态字段置为 0，支持批量 ids */
    private JSONObject doDisable(BizMeta meta, ActionContext ctx) throws Exception {
        return doStatusUpdate(meta, ctx, "disable", 0);
    }

    /** 状态字段更新通用方法（enable=1 / disable=0），支持单条和批量 */
    private JSONObject doStatusUpdate(BizMeta meta, ActionContext ctx, String action, int statusValue) throws Exception {
        JSONObject payload = ctx.getPayload();
        String pk = getPk(meta);
        String statusField = meta.getStatusField();
        if (statusField == null || statusField.isEmpty()) {
            throw new IllegalArgumentException("业务 " + meta.getBizCode() + " 未配置状态字段（statusField），无法执行 " + action + " 操作，请在元数据管理中配置状态字段");
        }
        String tableName = meta.getMainTable();
        List<String> idList = new ArrayList<>();
        JSONArray ids = payload.getJSONArray("ids");
        if (ids != null && !ids.isEmpty()) {
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.getString(i);
                if (id != null && !id.isEmpty()) idList.add(id);
            }
        } else {
            String id = payload.getString(pk);
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException(action + " 操作缺少 " + pk);
            }
            idList.add(id);
        }
        // 直接用 JdbcTemplate 执行参数化 SQL，绕过 SqlEngine 的 typeHint 类型转换
        // （typeHint 会将 varchar 列的 "1" 转为 Long，导致 PG 报 character varying = integer）
        String sql = "UPDATE " + tableName + " SET " + statusField + " = ? WHERE " + pk + " = ?";
        MultiDataSourceHolder.setDatasource(DB_NAME);
        int affected = 0;
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            for (String id : idList) {
                affected += jdbc.update(sql, statusValue, id);
            }
        } finally {
            MultiDataSourceHolder.clearDataSource();
        }
        log.info("[CrudExecutor] {} 成功 bizCode={} table={} count={}", action, meta.getBizCode(), tableName, affected);
        JSONObject result = new JSONObject();
        result.put("affectedRows", affected);
        return result;
    }

    private JSONObject doQuery(BizMeta meta, ActionContext ctx) throws Exception {
        JSONObject payload = ctx.getPayload();
        String pk = getPk(meta);
        JSONObject req = new JSONObject();
        req.put("tableName", meta.getMainTable());
        String alias = meta.getMainTableAlias();
        if (alias != null && !alias.isEmpty()) {
            req.put("alias", alias);
        }
        JSONArray readableCols = getReadableColumns(meta, ctx);
        if (readableCols != null && !readableCols.isEmpty()) {
            if (!readableCols.contains(pk)) {
                readableCols.add(0, pk);
            }
            req.put("columns", readableCols);
        }
        Integer page = payload.getInteger("page");
        Integer pageSize = payload.getInteger("pageSize");
        if (page != null && pageSize != null) {
            req.put("page", page);
            req.put("pageSize", pageSize);
        } else if (pageSize != null) {
            req.put("pageSize", pageSize);
        }
        // 排序：前端传 sort 为 JSON 字符串 {"selector":"col","desc":false,"TableAlias":"T"}
        String sortStr = payload.getString("sort");
        if (sortStr != null && !sortStr.isEmpty()) {
            try {
                JSONObject sortObj = JSONObject.parseObject(sortStr);
                if (sortObj != null) {
                    String selector = sortObj.getString("selector");
                    if (selector != null && !selector.isEmpty()) {
                        JSONArray orderBy = new JSONArray();
                        JSONObject ob = new JSONObject();
                        String tableAlias = sortObj.getString("TableAlias");
                        if (tableAlias != null && !tableAlias.isEmpty()) {
                            ob.put("column", tableAlias + "." + selector);
                        } else {
                            ob.put("column", selector);
                        }
                        ob.put("direction", sortObj.getBooleanValue("desc") ? "desc" : "asc");
                        orderBy.add(ob);
                        req.put("orderBy", orderBy);
                    }
                }
            } catch (Exception e) {
                // sort 解析失败，忽略排序
            }
        }
        JSONArray conditions = new JSONArray();
        JSONObject connector = new JSONObject();
        connector.put("Symbol", "and");
        conditions.add(connector);
        String logicDeleteField = meta.getLogicDeleteField();
        if (logicDeleteField != null && !logicDeleteField.isEmpty()) {
            JSONObject cond = new JSONObject();
            cond.put("Id", logicDeleteField);
            cond.put("Symbol", "=");
            cond.put("Val", false);
            cond.put("TableAlias", alias != null ? alias : "T");
            conditions.add(cond);
        }
        String filterData = payload.getString("filterData");
        if (filterData != null && !filterData.isEmpty()) {
            JSONArray extra = JSONArray.parseArray(filterData);
            if (extra != null) {
                for (int i = 0; i < extra.size(); i++) {
                    conditions.add(extra.get(i));
                }
            }
        }
        JSONArray dataScopeConds = buildDataScopeConditions(meta, ctx);
        if (dataScopeConds != null) {
            for (int i = 0; i < dataScopeConds.size(); i++) {
                conditions.add(dataScopeConds.get(i));
            }
        }
        if (conditions.size() > 1) {
            req.put("conditions", conditions);
        }
        ResMsg r = sqlEngineFacade.query(req, DB_NAME);
        if (!r.getRes()) {
            throw new Exception(r.getMsg());
        }
        JSONArray data = (JSONArray) r.getData();
        data = mapColumnNames(data, meta);
        // 基础资料关联字段缓存查询回填
        JSONArray baseDataRefs = payload.getJSONArray("baseDataRefs");
        if (baseDataRefs == null || baseDataRefs.isEmpty()) {
            baseDataRefs = loadBaseDataRefsFromMeta(meta.getMainTable());
        }
        if (baseDataRefs != null && !baseDataRefs.isEmpty()) {
            enrichBaseDataFields(data, baseDataRefs);
        }
        JSONObject result = new JSONObject();
        result.put("data", data);
        int totalCount;
        if (page != null && pageSize != null) {
            JSONObject countReq = new JSONObject();
            countReq.put("tableName", meta.getMainTable());
            countReq.put("conditions", conditions);
            ResMsg cr = sqlEngineFacade.count(countReq, DB_NAME);
            totalCount = cr.getRes() ? ((Number) cr.getData()).intValue() : (data != null ? data.size() : 0);
        } else {
            totalCount = data != null ? data.size() : 0;
        }
        result.put("totalCount", totalCount);
        return result;
    }

    /** SqlExecutor 返回大写列名，映射回元数据中的原始字段名（dbField） */
    private JSONArray mapColumnNames(JSONArray rows, BizMeta meta) {
        if (rows == null || rows.isEmpty()) return rows;
        java.util.Map<String, String> colMap = new java.util.HashMap<>();
        for (FieldMeta f : meta.getFields()) {
            String fn = f.getFieldName();
            if (fn != null) colMap.put(fn.toUpperCase(), fn);
        }
        String[] sysCols = {"id", "created_at", "updated_at", "created_by", "updated_by", "deleted"};
        for (String c : sysCols) colMap.put(c.toUpperCase(), c);

        JSONArray mapped = new JSONArray();
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            JSONObject newRow = new JSONObject();
            for (String key : row.keySet()) {
                String mappedKey = colMap.getOrDefault(key, key.toLowerCase());
                newRow.put(mappedKey, row.get(key));
            }
            mapped.add(newRow);
        }
        return mapped;
    }

    /** 从 lc_field_meta 自动加载基础资料关联配置，构建 baseDataRefs */
    private JSONArray loadBaseDataRefsFromMeta(String tableName) {
        if (tableName == null || tableName.isEmpty()) return null;
        try {
            JSONObject qReq = new JSONObject();
            qReq.put("tableName", "lc_field_meta");
            JSONArray conds = new JSONArray();
            JSONObject c0 = new JSONObject();
            c0.put("Symbol", "and");
            conds.add(c0);
            JSONObject c1 = new JSONObject();
            c1.put("Id", "biz_code");
            c1.put("Symbol", "=");
            c1.put("Val", tableName);
            conds.add(c1);
            qReq.put("conditions", conds);
            JSONArray cols = new JSONArray();
            cols.add("field_name");
            cols.add("base_data_ref");
            qReq.put("columns", cols);
            ResMsg r = sqlEngineFacade.query(qReq, DB_NAME);
            if (!r.getRes() || r.getData() == null) return null;
            JSONArray rows = (JSONArray) r.getData();
            JSONArray result = new JSONArray();
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String fieldName = row.getString("FIELD_NAME");
                if (fieldName == null) fieldName = row.getString("field_name");
                String baseDataRef = row.getString("BASE_DATA_REF");
                if (baseDataRef == null) baseDataRef = row.getString("base_data_ref");
                if (fieldName == null || baseDataRef == null || baseDataRef.isEmpty()) continue;
                JSONObject ref = JSONObject.parseObject(baseDataRef);
                String refPageId = ref.getString("refPageId");
                String valueField = ref.getString("valueField");
                String displayField = ref.getString("displayField");
                if (refPageId == null || valueField == null || displayField == null) continue;
                String[] displayFields = displayField.split(",");
                for (String df : displayFields) {
                    df = df.trim();
                    if (df.isEmpty()) continue;
                    JSONObject entry = new JSONObject();
                    entry.put("dataField", fieldName + "." + df);
                    entry.put("refPageId", refPageId);
                    entry.put("valueField", valueField);
                    entry.put("sourceField", fieldName);
                    result.add(entry);
                }
            }
            if (result.isEmpty()) {
                log.info("[baseDataRefs] table={} 无 baseDataRefs", tableName);
            } else {
                log.info("[baseDataRefs] table={} 加载 {} 条: {}", tableName, result.size(), result.toJSONString());
            }
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            log.warn("自动加载 baseDataRefs 失败: table={}, err={}", tableName, e.getMessage());
            return null;
        }
    }

    /** 基础资料关联字段缓存查询回填 */
    private void enrichBaseDataFields(JSONArray data, JSONArray baseDataRefs) {
        enrichBaseDataFields(data, baseDataRefs, 0);
    }

    private void enrichBaseDataFields(JSONArray data, JSONArray baseDataRefs, int depth) {
        if (depth > 5) return;
        if (data == null || data.isEmpty() || baseDataRefs == null || baseDataRefs.isEmpty()) return;
        Map<String, String> pageTableCache = new HashMap<>();
        for (int i = 0; i < baseDataRefs.size(); i++) {
            JSONObject ref = baseDataRefs.getJSONObject(i);
            String dataField = ref.getString("dataField");
            String refPageId = ref.getString("refPageId");
            String valueField = ref.getString("valueField");
            String sourceField = ref.getString("sourceField");
            if (dataField == null || refPageId == null || valueField == null || sourceField == null) continue;

            String remaining = dataField.startsWith(sourceField + ".")
                    ? dataField.substring(sourceField.length() + 1)
                    : (dataField.contains(".") ? dataField.substring(dataField.lastIndexOf(".") + 1) : dataField);
            boolean isNested = remaining.contains(".");
            String queryField = isNested ? remaining.substring(0, remaining.indexOf(".")) : remaining;
            String lookupField = isNested ? remaining : queryField;
            log.info("[enrich] dataField={}, sourceField={}, remaining={}, isNested={}, queryField={}, lookupField={}, refPageId={}",
                    dataField, sourceField, remaining, isNested, queryField, lookupField, refPageId);

            String tableName = pageTableCache.get(refPageId);
            if (tableName == null) {
                try {
                    JSONObject qReq = new JSONObject();
                    qReq.put("tableName", "lowcode_page");
                    JSONArray conds = new JSONArray();
                    JSONObject c0 = new JSONObject();
                    c0.put("Symbol", "and");
                    conds.add(c0);
                    JSONObject c1 = new JSONObject();
                    c1.put("Id", "page_id");
                    c1.put("Symbol", "=");
                    c1.put("Val", refPageId);
                    conds.add(c1);
                    qReq.put("conditions", conds);
                    JSONArray cols = new JSONArray();
                    cols.add("table_name");
                    qReq.put("columns", cols);
                    ResMsg r = sqlEngineFacade.query(qReq, DB_NAME);
                    if (r.getRes() && r.getData() != null) {
                        JSONArray rows = (JSONArray) r.getData();
                        if (!rows.isEmpty()) {
                            JSONObject row0 = rows.getJSONObject(0);
                            tableName = row0.getString("TABLE_NAME");
                            if (tableName == null) tableName = row0.getString("table_name");
                        }
                    }
                    pageTableCache.put(refPageId, tableName != null ? tableName : "");
                } catch (Exception e) {
                    log.warn("查询基础资料页面表名失败: refPageId={}, err={}", refPageId, e.getMessage());
                    continue;
                }
            }
            if (tableName == null || tableName.isEmpty()) continue;

            java.util.Set<Object> ids = new java.util.LinkedHashSet<>();
            for (int j = 0; j < data.size(); j++) {
                JSONObject row = data.getJSONObject(j);
                Object val = row.get(sourceField);
                if (val == null) val = row.get(sourceField.toLowerCase());
                if (val == null) val = row.get(sourceField.toUpperCase());
                if (val != null) ids.add(val);
            }
            if (ids.isEmpty()) continue;

            try {
                JSONObject qReq = new JSONObject();
                qReq.put("tableName", tableName);
                JSONArray cols = new JSONArray();
                cols.add(valueField);
                cols.add(queryField);
                qReq.put("columns", cols);
                JSONArray conds = new JSONArray();
                JSONObject c0 = new JSONObject();
                c0.put("Symbol", "and");
                conds.add(c0);
                JSONObject c1 = new JSONObject();
                c1.put("Id", valueField);
                c1.put("Symbol", "in");
                JSONArray idArray = new JSONArray();
                for (Object id : ids) {
                    if (id instanceof String) {
                        String s = (String) id;
                        try { idArray.add(Long.parseLong(s)); } catch (NumberFormatException e1) {
                            try { idArray.add(java.math.BigDecimal.valueOf(Double.parseDouble(s)).toBigInteger()); } catch (NumberFormatException e2) {
                                idArray.add(s);
                            }
                        }
                    } else {
                        idArray.add(id);
                    }
                }
                c1.put("Val", idArray);
                conds.add(c1);
                qReq.put("conditions", conds);
                ResMsg r = sqlEngineFacade.query(qReq, DB_NAME);
                if (!r.getRes() || r.getData() == null) {
                    log.warn("基础资料关联查询失败: table={}, field={}, valueField={}, ids={}, msg={}", tableName, dataField, valueField, ids, r.getMsg());
                    continue;
                }
                JSONArray bdRows = (JSONArray) r.getData();

                if (isNested) {
                    JSONArray refBaseDataRefs = loadBaseDataRefsFromMeta(tableName);
                    log.info("[enrichNested] dataField={}, table={}, queryField={}, lookupField={}, bdRows={}, refBaseDataRefs={}",
                            dataField, tableName, queryField, lookupField, bdRows.size(), refBaseDataRefs != null ? refBaseDataRefs.size() : 0);
                    if (refBaseDataRefs != null) {
                        enrichBaseDataFields(bdRows, refBaseDataRefs, depth + 1);
                    }
                }

                Map<String, Object> valueMap = new HashMap<>();
                String vfUpper = valueField.toUpperCase();
                String vfLower = valueField.toLowerCase();
                String lfUpper = lookupField.toUpperCase();
                String lfLower = lookupField.toLowerCase();
                for (int j = 0; j < bdRows.size(); j++) {
                    JSONObject bdRow = bdRows.getJSONObject(j);
                    Object key = bdRow.get(vfUpper);
                    if (key == null) key = bdRow.get(vfLower);
                    if (key == null) key = bdRow.get(valueField);
                    Object val = bdRow.get(lookupField);
                    if (val == null) val = bdRow.get(lfUpper);
                    if (val == null) val = bdRow.get(lfLower);
                    if (key != null) valueMap.put(String.valueOf(key), val);
                }

                if (valueMap.isEmpty()) {
                    log.warn("基础资料关联值映射为空: table={}, field={}, valueField={}, ids={}, bdRows={}", tableName, dataField, valueField, ids, bdRows.size());
                }

                for (int j = 0; j < data.size(); j++) {
                    JSONObject row = data.getJSONObject(j);
                    Object key = row.get(sourceField);
                    if (key == null) key = row.get(sourceField.toLowerCase());
                    if (key == null) key = row.get(sourceField.toUpperCase());
                    if (key != null && valueMap.containsKey(String.valueOf(key))) {
                        row.put(dataField, valueMap.get(String.valueOf(key)));
                    }
                }
            } catch (Exception e) {
                log.warn("查询基础资料关联字段失败: table={}, field={}, err={}", tableName, dataField, e.getMessage());
            }
        }
    }

    private void validateFields(BizMeta meta, JSONObject data, boolean checkRequired) throws Exception {
        for (FieldMeta f : meta.getFields()) {
            String fieldName = f.getFieldName();
            if (checkRequired && Boolean.TRUE.equals(f.getIsRequired()) && data.get(fieldName) == null) {
                throw new BizException("FIELD_REQUIRED", "必填字段缺失: " + fieldName);
            }
            String validationExpr = f.getValidationExpr();
            if (validationExpr != null && !validationExpr.isEmpty() && data.get(fieldName) != null) {
                Map<String, Object> vars = new HashMap<>(data);
                vars.put("value", data.get(fieldName));
                vars.put("field", fieldName);
                if (!spelExecutor.evaluateBoolean(validationExpr, vars)) {
                    throw new BizException("VALIDATION_FAILED",
                            "字段校验失败: " + fieldName + " 不满足 " + validationExpr);
                }
            }
        }
    }

    private JSONObject doStatusTransition(BizMeta meta, String action, ActionContext ctx, ActionMeta am) throws Exception {
        JSONObject payload = ctx.getPayload();
        String id = payload.getString("id");
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException(action + " 操作缺少 id");
        }
        JSONObject result = new JSONObject();
        result.put("id", id);
        result.put("action", action);
        result.put("statusTransition", am.getStatusTransition());
        log.info("[CrudExecutor] 状态迁移动作 {} 由 StateMachine 处理 bizCode={} id={}", action, meta.getBizCode(), id);
        return result;
    }

    private void applyDefaultValues(BizMeta meta, JSONObject data, boolean isCreate, ActionContext ctx) {
        String now = java.time.Instant.now().toString();
        String userId = ctx != null ? ctx.getUserId() : null;
        for (FieldMeta f : meta.getFields()) {
            String fieldName = f.getFieldName();
            if ("created_at".equals(fieldName) && isCreate && isEmptyValue(data.get(fieldName))) {
                data.put(fieldName, now);
                continue;
            }
            if ("updated_at".equals(fieldName) && isEmptyValue(data.get(fieldName))) {
                data.put(fieldName, now);
                continue;
            }
            if ("created_by".equals(fieldName) && isCreate && isEmptyValue(data.get(fieldName))) {
                data.put(fieldName, userId);
                continue;
            }
            if ("updated_by".equals(fieldName) && isEmptyValue(data.get(fieldName))) {
                data.put(fieldName, userId);
                continue;
            }
            String expr = f.getDefaultValueExpr();
            if (isCreate && isEmptyValue(data.get(fieldName)) && expr != null && !expr.trim().isEmpty()) {
                String trimmed = expr.trim();
                if (trimmed.startsWith("{")) {
                    // 编码规则 JSON：由 NumberRuleEngine 按多段规则生成（常量/日期/流水号等）
                    String code = numberRuleEngine.generate(trimmed, data, meta.getBizCode(), fieldName);
                    if (code != null && !code.isEmpty()) {
                        data.put(fieldName, code);
                    }
                } else if ("now()".equalsIgnoreCase(trimmed)) {
                    data.put(fieldName, now);
                } else if ("false".equalsIgnoreCase(trimmed)) {
                    data.put(fieldName, false);
                } else if ("true".equalsIgnoreCase(trimmed)) {
                    data.put(fieldName, true);
                } else if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
                    data.put(fieldName, trimmed.substring(1, trimmed.length() - 1));
                } else {
                    try {
                        Object spelResult = spelExecutor.evaluate(trimmed, data);
                        if (spelResult != null) {
                            data.put(fieldName, spelResult);
                        }
                    } catch (Exception e) {
                        log.debug("[CrudExecutor] defaultValueExpr 求值失败 field={} expr={}", fieldName, trimmed);
                    }
                }
            }
        }
    }

    /** 值为空（null 或空白字符串）时视为未填，触发默认值/编码生成 */
    private boolean isEmptyValue(Object v) {
        return v == null || (v instanceof String && ((String) v).trim().isEmpty());
    }

    /** 断号回收：遍历行中配置了编码规则的字段，把编码中的流水号放回回收池（seqGapFill=true 时） */
    private void recycleSequenceNumbers(BizMeta meta, JSONObject row) {
        for (FieldMeta f : meta.getFields()) {
            String expr = f.getDefaultValueExpr();
            if (expr == null || !expr.trim().startsWith("{")) continue;
            String fieldName = f.getFieldName();
            Object v = row.get(fieldName);
            if (v == null) v = row.get(fieldName.toLowerCase());
            if (v == null) v = row.get(fieldName.toUpperCase());
            if (v == null || String.valueOf(v).trim().isEmpty()) continue;
            try {
                numberRuleEngine.recycle(expr.trim(), String.valueOf(v).trim(), row, meta.getBizCode(), fieldName);
            } catch (Exception e) {
                log.debug("[CrudExecutor] 断号回收失败 field={}: {}", fieldName, e.getMessage());
            }
        }
    }

    private void filterWritableFields(BizMeta meta, JSONObject data, ActionContext ctx) {
        for (FieldMeta f : meta.getFields()) {
            String permissionExpr = f.getPermissionExpr();
            if (permissionExpr == null || permissionExpr.isEmpty()) {
                continue;
            }
            String fieldName = f.getFieldName();
            if (!data.containsKey(fieldName)) {
                continue;
            }
            Map<String, Object> vars = new HashMap<>();
            if (ctx.getContext() != null) {
                vars.putAll(ctx.getContext());
            }
            vars.put("action", ctx.getAction());
            vars.put("mode", "write");
            try {
                if (!spelExecutor.evaluateBoolean(permissionExpr, vars)) {
                    data.remove(fieldName);
                    log.debug("[CrudExecutor] 字段无写权限已移除 field={}", fieldName);
                }
            } catch (Exception e) {
                log.debug("[CrudExecutor] permissionExpr 求值失败 field={} expr={}", fieldName, permissionExpr);
            }
        }
    }

    private JSONArray getReadableColumns(BizMeta meta, ActionContext ctx) {
        JSONArray cols = new JSONArray();
        for (FieldMeta f : meta.getFields()) {
            String permissionExpr = f.getPermissionExpr();
            if (permissionExpr != null && !permissionExpr.isEmpty()) {
                Map<String, Object> vars = new HashMap<>();
                if (ctx.getContext() != null) {
                    vars.putAll(ctx.getContext());
                }
                vars.put("action", ctx.getAction());
                vars.put("mode", "read");
                try {
                    if (!spelExecutor.evaluateBoolean(permissionExpr, vars)) {
                        continue;
                    }
                } catch (Exception e) {
                    continue;
                }
            }
            cols.add(f.getFieldName());
        }
        return cols;
    }

    private JSONArray buildDataScopeConditions(BizMeta meta, ActionContext ctx) {
        ActionMeta actionMeta = meta.getAction(ctx.getAction());
        if (actionMeta == null || actionMeta.getDataScopeExpr() == null
                || actionMeta.getDataScopeExpr().isEmpty()) {
            return null;
        }
        log.debug("[CrudExecutor] dataScopeExpr={} bizCode={}", actionMeta.getDataScopeExpr(), meta.getBizCode());
        return null;
    }

    private JSONArray mergeDataScopeConditions(BizMeta meta, ActionContext ctx, JSONArray baseConditions) {
        JSONArray dataScopeConds = buildDataScopeConditions(meta, ctx);
        if (dataScopeConds == null || dataScopeConds.isEmpty()) {
            return baseConditions;
        }
        JSONArray merged = new JSONArray();
        for (int i = 0; i < baseConditions.size(); i++) {
            merged.add(baseConditions.get(i));
        }
        for (int i = 0; i < dataScopeConds.size(); i++) {
            merged.add(dataScopeConds.get(i));
        }
        return merged;
    }

    // ==================== 数据审计 ====================

    /**
     * 审计挂钩：diff 变更字段，事务提交后（afterCommit）将审计记录放入内存队列。
     */
    private void recordAuditAfterCommit(BizMeta meta, ActionContext ctx, String recordId,
            JSONObject beforeRow, JSONObject newData) {
        try {
            JSONObject beforeDiff = new JSONObject();
            JSONObject afterDiff = new JSONObject();
            List<String> changedFields = new ArrayList<>();
            for (String key : newData.keySet()) {
                Object newVal = newData.get(key);
                Object oldVal = beforeRow.get(key.toUpperCase());
                if (oldVal == null) oldVal = beforeRow.get(key);
                if (oldVal == null) oldVal = beforeRow.get(key.toLowerCase());
                if (!java.util.Objects.equals(newVal, oldVal)) {
                    beforeDiff.put(key, oldVal);
                    afterDiff.put(key, newVal);
                    changedFields.add(key);
                }
            }
            if (changedFields.isEmpty()) {
                log.info("[审计] 无变更字段，跳过 bizCode={} recordId={}", meta.getBizCode(), recordId);
                return;
            }

            String changedFieldsStr = String.join(",", changedFields);
            String operator = ctx != null ? ctx.getUserId() : null;
            DataAuditRecord record = DataAuditRecord.create(
                    meta.getBizCode(), meta.getMainTable(), recordId,
                    "update", changedFieldsStr, beforeDiff.toJSONString(), afterDiff.toJSONString(),
                    operator, null);
            log.info("[审计] 变更字段={} beforeDiff={} afterDiff={}", changedFieldsStr, beforeDiff.toJSONString(), afterDiff.toJSONString());

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        log.info("[审计] afterCommit 回调触发，入队 recordId={}", recordId);
                        dataAuditService.offer(record);
                    }
                });
                log.info("[审计] 已注册 afterCommit 同步");
            } else {
                log.info("[审计] 事务同步未激活，直接入队 recordId={}", recordId);
                dataAuditService.offer(record);
            }
        } catch (Exception e) {
            log.warn("[CrudExecutor] 审计记录入队失败（不影响业务）: {}", e.getMessage());
        }
    }

    /**
     * 查询某行的修改历史，可选反向重放还原某版本完整数据。
     */
    private JSONObject doAuditHistory(BizMeta meta, ActionContext ctx) throws Exception {
        JSONObject payload = ctx.getPayload();
        String pk = getPk(meta);
        String id = payload.getString("recordId");
        if (id == null || id.isEmpty()) {
            id = payload.getString(pk);
        }
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("auditHistory 操作缺少 recordId");
        }

        JSONArray conditions = new JSONArray();
        JSONObject connector = new JSONObject();
        connector.put("Symbol", "and");
        conditions.add(connector);
        JSONObject c1 = new JSONObject();
        c1.put("Id", "table_name");
        c1.put("Symbol", "=");
        c1.put("Val", meta.getMainTable());
        c1.put("TableAlias", "T");
        conditions.add(c1);
        JSONObject c2 = new JSONObject();
        c2.put("Id", "record_id");
        c2.put("Symbol", "=");
        c2.put("Val", id);
        c2.put("TableAlias", "T");
        conditions.add(c2);

        String[][] mapping = {
            {"ID", "id"}, {"BIZ_CODE", "bizCode"}, {"TABLE_NAME", "tableName"},
            {"RECORD_ID", "recordId"}, {"ACTION", "action"}, {"CHANGED_FIELDS", "changedFields"},
            {"BEFORE_DATA", "beforeData"}, {"AFTER_DATA", "afterData"},
            {"OPERATOR", "operator"}, {"OPERATED_AT", "operatedAt"}, {"IP", "ip"}
        };

        JSONArray history = helper.queryAndMap("lc_data_audit_log", null, conditions, "operated_at", false, mapping);

        JSONObject result = new JSONObject();
        result.put("history", history);

        String restoreVersionId = payload.getString("restoreVersionId");
        if (restoreVersionId != null && !restoreVersionId.isEmpty()) {
            JSONArray currentRows = helper.query(meta.getMainTable(), null, helper.eq(pk, id), null, false);
            if (currentRows != null && !currentRows.isEmpty()) {
                JSONObject currentData = currentRows.getJSONObject(0);
                JSONObject restored = restoreVersion(currentData, history, restoreVersionId);
                result.put("restoredData", restored);
            }
        }

        return result;
    }

    /**
     * 反向重放：从当前数据开始，从最新到目标版本逐个撤销变更。
     */
    private JSONObject restoreVersion(JSONObject currentData, JSONArray history, String targetVersionId) {
        JSONObject restored = new JSONObject();
        for (String key : currentData.keySet()) {
            restored.put(key.toLowerCase(), currentData.get(key));
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            JSONObject record = history.getJSONObject(i);
            String recordId = record.getString("id");
            if (targetVersionId.equals(recordId)) {
                break;
            }
            String beforeJson = record.getString("beforeData");
            if (beforeJson != null && !beforeJson.isEmpty()) {
                JSONObject before = JSONObject.parseObject(beforeJson);
                for (String field : before.keySet()) {
                    restored.put(field, before.get(field));
                }
            }
        }
        return restored;
    }
}
