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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
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

    private static final String DB_NAME = "lowcode";

    public Object execute(BizMeta meta, String action, ActionContext ctx) throws Exception {
        ActionMeta actionMeta = meta.getAction(action);
        if (actionMeta == null || !actionMeta.isEnabled()) {
            throw new IllegalArgumentException("业务 " + meta.getBizCode() + " 不支持操作: " + action);
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
        String pk = getPk(meta);
        if (isSystemPk(meta, pk) && data.getString(pk) == null) {
            if ("SNOWFLAKE".equalsIgnoreCase(meta.getKeyStrategy())) {
                data.put(pk, SnowflakeIdGenerator.nextId());
            } else {
                data.put(pk, UUID.randomUUID().toString().replace("-", ""));
            }
        }
        applyDefaultValues(meta, data, true);
        validateFields(meta, data, true);
        filterWritableFields(meta, data, ctx);
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
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("update 操作缺少 " + pk);
        }
        JSONObject data = new JSONObject(payload);
        data.remove(pk);
        applyDefaultValues(meta, data, false);
        validateFields(meta, data, false);
        filterWritableFields(meta, data, ctx);
        JSONArray conditions = helper.eq(pk, id);
        conditions = mergeDataScopeConditions(meta, ctx, conditions);
        ResMsg r = helper.update(meta.getMainTable(), data, conditions);
        SqlEngineJson.check(r);
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
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("delete 操作缺少 " + pk);
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

    private void applyDefaultValues(BizMeta meta, JSONObject data, boolean isCreate) {
        String now = java.time.Instant.now().toString();
        for (FieldMeta f : meta.getFields()) {
            String fieldName = f.getFieldName();
            if ("created_at".equals(fieldName) && isCreate && data.getString(fieldName) == null) {
                data.put(fieldName, now);
                continue;
            }
            if ("updated_at".equals(fieldName) && data.getString(fieldName) == null) {
                data.put(fieldName, now);
                continue;
            }
            if (isCreate && data.get(fieldName) == null && f.getDefaultValueExpr() != null) {
                String expr = f.getDefaultValueExpr();
                if ("now()".equalsIgnoreCase(expr)) {
                    data.put(fieldName, now);
                } else if ("false".equalsIgnoreCase(expr)) {
                    data.put(fieldName, false);
                } else if ("true".equalsIgnoreCase(expr)) {
                    data.put(fieldName, true);
                } else if (expr.startsWith("'") && expr.endsWith("'")) {
                    data.put(fieldName, expr.substring(1, expr.length() - 1));
                } else {
                    try {
                        Object spelResult = spelExecutor.evaluate(expr, data);
                        if (spelResult != null) {
                            data.put(fieldName, spelResult);
                        }
                    } catch (Exception e) {
                        log.debug("[CrudExecutor] defaultValueExpr 求值失败 field={} expr={}", fieldName, expr);
                    }
                }
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
}
