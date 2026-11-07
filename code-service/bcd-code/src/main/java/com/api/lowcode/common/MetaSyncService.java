package com.api.lowcode.common;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.meta.MetaRegistry;
import com.server.db.MultiDataSourceHolder;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;

/**
 * 元数据同步服务：表单页面保存时自动同步 lc_biz_meta / lc_field_meta / lc_action_meta。
 *
 * <p>
 * 用 tableName 作为 bizCode，从 bizSchemas.form 提取表单控件的 dbField 生成字段元数据，
 * 确保 query/create/update/delete 四个基本操作存在，最后刷新 MetaRegistry 缓存。
 * </p>
 */
@Slf4j
@Service
public class MetaSyncService {

    @Resource
    private SqlEngineJson helper;

    @Resource
    private MetaRegistry metaRegistry;

    @Resource(name = "multiDataSource")
    private DataSource dataSource;

    private volatile boolean exprColumnWidened = false;

    private static final Map<String, String> TYPE_MAP = new HashMap<>();

    static {
        TYPE_MAP.put("dx-text-box", "VARCHAR");
        TYPE_MAP.put("dx-text-area", "TEXT");
        TYPE_MAP.put("dx-number-box", "DECIMAL");
        TYPE_MAP.put("dx-date-box", "TIMESTAMP");
        TYPE_MAP.put("dx-date-time-box", "TIMESTAMP");
        TYPE_MAP.put("dx-check-box", "BOOLEAN");
        TYPE_MAP.put("dx-switch", "BOOLEAN");
        TYPE_MAP.put("dx-select-box", "VARCHAR");
        TYPE_MAP.put("dx-color-box", "VARCHAR");
        TYPE_MAP.put("dx-tag-box", "VARCHAR");
        TYPE_MAP.put("dx-lookup", "VARCHAR");
        TYPE_MAP.put("dx-auto-complete", "VARCHAR");
        TYPE_MAP.put("dx-radio-group", "VARCHAR");
        TYPE_MAP.put("dx-slider", "DECIMAL");
        TYPE_MAP.put("dx-range-slider", "VARCHAR");
        TYPE_MAP.put("dx-progress-bar", "DECIMAL");
    }

    private static final String DEFAULT_DATA_TYPE = "VARCHAR";

    public void syncMeta(JSONObject schema) {
        String pageType = schema.getString("pageType");
        if (!"document".equals(pageType) && !"basedata".equals(pageType)) {
            return;
        }

        String tableName = schema.getString("tableName");
        if (tableName == null || tableName.trim().isEmpty()) {
            return;
        }
        tableName = tableName.trim();
        // 系统管理配置页面（sys_ 前缀 bizCode）的元数据由 bcd-system SystemPageSeeder 维护
        // （写操作为 EXTERNAL_API 桥接），设计器保存时跳过，防止 ensure 的 SINGLE action 覆盖种子
        if (tableName.startsWith("sys_")) {
            return;
        }

        ensureExprColumnText();

        String bizCode = tableName;
        String bizName = schema.getString("pageName");
        String now = Instant.now().toString();

        try {
            JSONObject bizMeta = new JSONObject();
            bizMeta.put("id", "meta_" + bizCode);
            bizMeta.put("biz_code", bizCode);
            bizMeta.put("biz_name", bizName != null ? bizName : bizCode);
            bizMeta.put("main_table", tableName);
            bizMeta.put("main_table_alias", "T");
            bizMeta.put("logic_delete_field", "deleted");
            bizMeta.put("status_field", "status");
            String pkField = schema.getString("primaryKeyField");
            bizMeta.put("primary_key_field", pkField != null && !pkField.trim().isEmpty() ? pkField.trim() : "id");
            String keyStrategy = schema.getString("keyStrategy");
            bizMeta.put("key_strategy", keyStrategy != null && !keyStrategy.trim().isEmpty() ? keyStrategy.trim() : "SNOWFLAKE");
            bizMeta.put("version", 1);
            bizMeta.put("remark", "由页面保存自动生成");
            bizMeta.put("created_at", now);
            Object plugins = schema.get("plugins");
            if (plugins != null) {
                bizMeta.put("plugins", plugins instanceof String ? plugins : com.alibaba.fastjson2.JSON.toJSONString(plugins));
            }
            helper.upsert("lc_biz_meta", bizMeta, helper.eq("biz_code", bizCode));

            List<FieldDef> fields = extractFormFields(schema);
            // 从表格列配置合并 baseDataRef 的 displayField
            mergeTableBaseDataRefs(schema, fields);
            // 清理已删除控件的字段元数据，避免查询时引用不存在的列
            java.util.Set<String> currentFieldNames = new java.util.HashSet<>();
            for (FieldDef f : fields) {
                currentFieldNames.add(f.name);
            }
            JSONArray existingFields = helper.query("lc_field_meta", new String[]{"id", "field_name"}, helper.eq("biz_code", bizCode), null, false);
            for (int i = 0; i < existingFields.size(); i++) {
                JSONObject existing = existingFields.getJSONObject(i);
                String existingName = existing.getString("FIELD_NAME");
                if (!currentFieldNames.contains(existingName)) {
                    helper.delete("lc_field_meta", helper.eq("id", existing.getString("ID")));
                }
            }
            for (FieldDef f : fields) {
                JSONObject fieldMeta = new JSONObject();
                fieldMeta.put("id", "field_" + bizCode + "_" + f.name);
                fieldMeta.put("biz_code", bizCode);
                fieldMeta.put("field_name", f.name);
                fieldMeta.put("column_type", "BUSINESS");
                fieldMeta.put("data_type", f.dataType);
                fieldMeta.put("is_required", f.required);
                fieldMeta.put("is_unique", f.unique);
                fieldMeta.put("is_system_field", false);
                fieldMeta.put("sort_no", f.sortNo);
                // 清除语义：取消组合唯一后显式写空串，避免 upsert 保留旧值
                fieldMeta.put("unique_group", f.uniqueGroup != null ? f.uniqueGroup : "");
                if (f.defaultValueExpr != null) {
                    // 编码规则 JSON，create 时空值由 NumberRuleEngine 按规则生成
                    fieldMeta.put("default_value_expr", f.defaultValueExpr);
                } else {
                    // 显式清空，避免 upsert 保留旧规则
                    fieldMeta.put("default_value_expr", "");
                }
                if (f.baseDataRef != null) {
                    fieldMeta.put("base_data_ref", f.baseDataRef);
                }
                fieldMeta.put("created_at", now);
                helper.upsert("lc_field_meta", fieldMeta, helper.eq("id", fieldMeta.getString("id")));
            }

            String[] defaultActions = {"query", "create", "update", "delete"};
            for (String action : defaultActions) {
                JSONObject actionMeta = new JSONObject();
                actionMeta.put("id", "action_" + bizCode + "_" + action);
                actionMeta.put("biz_code", bizCode);
                actionMeta.put("action", action);
                actionMeta.put("strategy_type", "SINGLE");
                actionMeta.put("enabled", true);
                actionMeta.put("created_at", now);
                helper.upsert("lc_action_meta", actionMeta, helper.eq("id", actionMeta.getString("id")));
            }

            metaRegistry.reload();
            log.info("[MetaSync] 同步元数据成功:F: bizCode={}, table={}, fields={}", bizCode, tableName, fields.size());

            syncDetailMeta(schema, now);
        } catch (Exception e) {
            log.error("[MetaSync] 同步元数据失败: bizCode={}, {}", bizCode, e.getMessage(), e);
        }
    }

    /** 同步明细表（data-grid with tableName）的元数据到 lc_biz_meta */
    private void syncDetailMeta(JSONObject schema, String now) {
        JSONArray formRoot = null;
        JSONObject bizSchemas = schema.getJSONObject("bizSchemas");
        if (bizSchemas != null) {
            formRoot = bizSchemas.getJSONArray("form");
        }
        if (formRoot == null) formRoot = schema.getJSONArray("root");
        if (formRoot == null) return;

        List<JSONObject> detailGrids = new ArrayList<>();
        collectDetailGrids(formRoot, detailGrids);

        String pagePkField = schema.getString("primaryKeyField");
        if (pagePkField == null || pagePkField.trim().isEmpty()) pagePkField = "id";

        for (JSONObject grid : detailGrids) {
            JSONObject gp = grid.getJSONObject("props");
            if (gp == null) continue;
            String dtTable = gp.getString("tableName");
            if (dtTable == null || dtTable.trim().isEmpty()) continue;
            dtTable = dtTable.trim();
            if (dtTable.startsWith("sys_")) continue;

            String dtKeyExpr = gp.getString("keyExpr");
            if (dtKeyExpr == null || dtKeyExpr.trim().isEmpty()) dtKeyExpr = "id";
            String dtKeyStrategy = gp.getString("keyStrategy");
            if (dtKeyStrategy == null || dtKeyStrategy.trim().isEmpty()) dtKeyStrategy = "SNOWFLAKE";
            String dtComment = gp.getString("tableComment");
            if (dtComment == null || dtComment.trim().isEmpty()) dtComment = dtTable;

            try {
                JSONObject dtBizMeta = new JSONObject();
                dtBizMeta.put("id", "meta_" + dtTable);
                dtBizMeta.put("biz_code", dtTable);
                dtBizMeta.put("biz_name", dtComment);
                dtBizMeta.put("main_table", dtTable);
                dtBizMeta.put("main_table_alias", "T");
                dtBizMeta.put("logic_delete_field", "deleted");
                dtBizMeta.put("status_field", "status");
                dtBizMeta.put("primary_key_field", dtKeyExpr);
                dtBizMeta.put("key_strategy", dtKeyStrategy);
                dtBizMeta.put("version", 1);
                dtBizMeta.put("remark", "由明细表同步自动生成");
                dtBizMeta.put("created_at", now);
                helper.upsert("lc_biz_meta", dtBizMeta, helper.eq("biz_code", dtTable));

                JSONArray columns = gp.getJSONArray("columns");
                int sortNo = 1;
                if (columns != null) {
                    for (int i = 0; i < columns.size(); i++) {
                        JSONObject col = columns.getJSONObject(i);
                        String dataField = col.getString("dataField");
                        if (dataField == null || dataField.trim().isEmpty()) continue;
                        String dataType = col.getString("dataType");
                        String colType = mapDetailColumnType(dataType);
                        JSONObject fieldMeta = new JSONObject();
                        fieldMeta.put("id", "field_" + dtTable + "_" + dataField);
                        fieldMeta.put("biz_code", dtTable);
                        fieldMeta.put("field_name", dataField);
                        fieldMeta.put("column_type", "BUSINESS");
                        fieldMeta.put("data_type", colType);
                        fieldMeta.put("is_required", col.getBooleanValue("required"));
                        fieldMeta.put("is_unique", false);
                        fieldMeta.put("is_system_field", false);
                        fieldMeta.put("sort_no", sortNo++);
                        fieldMeta.put("unique_group", "");
                        fieldMeta.put("created_at", now);
                        helper.upsert("lc_field_meta", fieldMeta, helper.eq("id", fieldMeta.getString("id")));
                    }
                }
                // 注册明细表自身主键字段到元数据（确保 mapColumnNames 不转小写）
                if (!dtKeyExpr.equals(pagePkField)) {
                    JSONObject pkFieldMeta = new JSONObject();
                    pkFieldMeta.put("id", "field_" + dtTable + "_" + dtKeyExpr);
                    pkFieldMeta.put("biz_code", dtTable);
                    pkFieldMeta.put("field_name", dtKeyExpr);
                    pkFieldMeta.put("column_type", "SYSTEM");
                    pkFieldMeta.put("data_type", "VARCHAR");
                    pkFieldMeta.put("is_required", true);
                    pkFieldMeta.put("is_unique", false);
                    pkFieldMeta.put("is_system_field", true);
                    pkFieldMeta.put("sort_no", 0);
                    pkFieldMeta.put("unique_group", "");
                    pkFieldMeta.put("created_at", now);
                    helper.upsert("lc_field_meta", pkFieldMeta, helper.eq("id", pkFieldMeta.getString("id")));
                }
                JSONObject fkFieldMeta = new JSONObject();
                fkFieldMeta.put("id", "field_" + dtTable + "_" + pagePkField);
                fkFieldMeta.put("biz_code", dtTable);
                fkFieldMeta.put("field_name", pagePkField);
                fkFieldMeta.put("column_type", "SYSTEM");
                fkFieldMeta.put("data_type", "VARCHAR");
                fkFieldMeta.put("is_required", true);
                fkFieldMeta.put("is_unique", false);
                fkFieldMeta.put("is_system_field", true);
                fkFieldMeta.put("sort_no", 0);
                fkFieldMeta.put("unique_group", "");
                fkFieldMeta.put("created_at", now);
                helper.upsert("lc_field_meta", fkFieldMeta, helper.eq("id", fkFieldMeta.getString("id")));

                // 子明细表（parentType=table）：额外添加父明细表外键字段到元数据
                String parentType = gp.getString("parentType");
                if ("table".equals(parentType)) {
                    String parentRef = gp.getString("parentRef");
                    if (parentRef != null && !parentRef.trim().isEmpty()) {
                        JSONObject parentGrid = findGridById(formRoot, parentRef);
                        if (parentGrid != null) {
                            JSONObject pp = parentGrid.getJSONObject("props");
                            String parentKeyExpr = pp != null ? pp.getString("keyExpr") : null;
                            if (parentKeyExpr != null && !parentKeyExpr.trim().isEmpty()
                                    && !parentKeyExpr.equals(pagePkField)) {
                                JSONObject parentFkMeta = new JSONObject();
                                parentFkMeta.put("id", "field_" + dtTable + "_" + parentKeyExpr);
                                parentFkMeta.put("biz_code", dtTable);
                                parentFkMeta.put("field_name", parentKeyExpr);
                                parentFkMeta.put("column_type", "SYSTEM");
                                parentFkMeta.put("data_type", "VARCHAR");
                                parentFkMeta.put("is_required", true);
                                parentFkMeta.put("is_unique", false);
                                parentFkMeta.put("is_system_field", true);
                                parentFkMeta.put("sort_no", 0);
                                parentFkMeta.put("unique_group", "");
                                parentFkMeta.put("created_at", now);
                                helper.upsert("lc_field_meta", parentFkMeta, helper.eq("id", parentFkMeta.getString("id")));
                            }
                        }
                    }
                }

                String[] defaultActions = {"query", "create", "update", "delete"};
                for (String action : defaultActions) {
                    JSONObject actionMeta = new JSONObject();
                    actionMeta.put("id", "action_" + dtTable + "_" + action);
                    actionMeta.put("biz_code", dtTable);
                    actionMeta.put("action", action);
                    actionMeta.put("strategy_type", "SINGLE");
                    actionMeta.put("enabled", true);
                    actionMeta.put("created_at", now);
                    helper.upsert("lc_action_meta", actionMeta, helper.eq("id", actionMeta.getString("id")));
                }
                log.info("[MetaSync] 明细表元数据同步成功: bizCode={}, pk={}, strategy={}", dtTable, dtKeyExpr, dtKeyStrategy);
            } catch (Exception e) {
                log.error("[MetaSync] 明细表元数据同步失败: {}, {}", dtTable, e.getMessage());
            }
        }
        if (!detailGrids.isEmpty()) {
            metaRegistry.reload();
        }
    }

    private void collectDetailGrids(JSONArray nodes, List<JSONObject> result) {
        if (nodes == null) return;
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if ("data-grid".equals(node.getString("type"))) {
                JSONObject np = node.getJSONObject("props");
                String tn = np != null ? np.getString("tableName") : null;
                if (tn != null && !tn.trim().isEmpty()) {
                    result.add(node);
                }
            }
            JSONArray children = node.getJSONArray("children");
            if (children != null) {
                collectDetailGrids(children, result);
            }
        }
    }

    /** 在 schema 树中按节点 id 查找 data-grid 节点 */
    private JSONObject findGridById(JSONArray nodes, String id) {
        if (nodes == null || id == null) return null;
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (id.equals(node.getString("id"))) return node;
            JSONArray children = node.getJSONArray("children");
            if (children != null) {
                JSONObject found = findGridById(children, id);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String mapDetailColumnType(String dataType) {
        if (dataType == null) return "VARCHAR";
        switch (dataType) {
            case "number": return "DECIMAL";
            case "date": return "TIMESTAMP";
            case "datetime": return "TIMESTAMP";
            case "boolean": return "BOOLEAN";
            default: return "VARCHAR";
        }
    }

    /** 从表格列配置中扫描 baseDataRef，合并到对应字段的 displayField 与 codeField */
    private void mergeTableBaseDataRefs(JSONObject schema, List<FieldDef> fields) {
        JSONObject bizSchemas = schema.getJSONObject("bizSchemas");
        if (bizSchemas == null) return;
        JSONArray tableRoot = bizSchemas.getJSONArray("table");
        if (tableRoot == null) return;
        Map<String, java.util.Set<String>> tableDisplayFields = new HashMap<>();
        Map<String, String> tableCodeFields = new HashMap<>();
        scanTableColumns(tableRoot, tableDisplayFields, tableCodeFields);
        boolean hasCodeFields = !tableCodeFields.isEmpty();
        if (tableDisplayFields.isEmpty() && !hasCodeFields) return;
        for (FieldDef f : fields) {
            if (f.baseDataRef == null) continue;
            String key = f.name.toUpperCase();
            java.util.Set<String> extraFields = tableDisplayFields.get(key);
            String colCodeField = tableCodeFields.get(key);
            if ((extraFields == null || extraFields.isEmpty()) && (colCodeField == null || colCodeField.isEmpty())) continue;
            try {
                JSONObject ref = JSONObject.parseObject(f.baseDataRef);
                boolean changed = false;
                if (extraFields != null && !extraFields.isEmpty()) {
                    String existingDisplay = ref.getString("displayField");
                    java.util.Set<String> allFields = new java.util.LinkedHashSet<>();
                    if (existingDisplay != null) {
                        for (String df : existingDisplay.split(",")) {
                            String t = df.trim();
                            if (!t.isEmpty()) allFields.add(t);
                        }
                    }
                    allFields.addAll(extraFields);
                    ref.put("displayField", String.join(",", allFields));
                    changed = true;
                }
                // 列级编码字段合并：表单控件未配 codeField 时以列级配置为准（导入反查匹配字段）
                if (colCodeField != null && !colCodeField.isEmpty()) {
                    String existingCode = ref.getString("codeField");
                    if (existingCode == null || existingCode.isEmpty()) {
                        ref.put("codeField", colCodeField);
                        changed = true;
                    }
                }
                if (changed) {
                    f.baseDataRef = ref.toJSONString();
                }
            } catch (Exception e) {
                log.warn("[MetaSync] 合并表格 baseDataRef 失败: field={}", f.name);
            }
        }
    }

    private void scanTableColumns(JSONArray nodes, Map<String, java.util.Set<String>> map,
                                  Map<String, String> codeFieldMap) {
        if (nodes == null) return;
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (node == null) continue;
            JSONObject props = node.getJSONObject("props");
            if (props != null) {
                JSONArray columns = props.getJSONArray("columns");
                if (columns != null) {
                    for (int j = 0; j < columns.size(); j++) {
                        JSONObject col = columns.getJSONObject(j);
                        String dataField = col.getString("dataField");
                        JSONObject baseDataRef = col.getJSONObject("baseDataRef");
                        if (dataField == null || baseDataRef == null) continue;
                        if (dataField.contains(".")) {
                            String sourceField = baseDataRef.getString("sourceField");
                            String displayField = dataField.substring(dataField.indexOf(".") + 1);
                            if (sourceField != null && !displayField.isEmpty()) {
                                map.computeIfAbsent(sourceField.toUpperCase(), k -> new java.util.LinkedHashSet<>()).add(displayField);
                            }
                        }
                        // 列级编码字段（ColumnEditorDialog 配置）：无点号列直接归属 dataField，带点号列归属 sourceField
                        String colCodeField = baseDataRef.getString("codeField");
                        if (colCodeField != null && !colCodeField.trim().isEmpty()) {
                            String owner = dataField.contains(".")
                                    ? baseDataRef.getString("sourceField") : dataField;
                            if (owner != null && !owner.trim().isEmpty()) {
                                codeFieldMap.putIfAbsent(owner.toUpperCase(), colCodeField.trim());
                            }
                        }
                    }
                }
            }
            JSONArray children = node.getJSONArray("children");
            if (children != null) scanTableColumns(children, map, codeFieldMap);
        }
    }

    private List<FieldDef> extractFormFields(JSONObject schema) {
        List<FieldDef> fields = new ArrayList<>();
        JSONObject bizSchemas = schema.getJSONObject("bizSchemas");
        if (bizSchemas == null) return fields;
        JSONArray formRoot = bizSchemas.getJSONArray("form");
        if (formRoot == null) return fields;
        Set<String> seen = new HashSet<>();
        int[] sortNo = {0};
        for (int i = 0; i < formRoot.size(); i++) {
            collectFields(formRoot.getJSONObject(i), fields, seen, sortNo);
        }
        return fields;
    }

    private void collectFields(JSONObject node, List<FieldDef> fields, Set<String> seen, int[] sortNo) {
        if (node == null) return;
        String dbField = node.getString("dbField");
        String type = node.getString("type");
        if (dbField != null && !dbField.trim().isEmpty() && !seen.contains(dbField)) {
            seen.add(dbField);
            String dataType = TYPE_MAP.getOrDefault(type, DEFAULT_DATA_TYPE);
            String baseDataRef = null;
            if ("base-data".equals(type)) {
                baseDataRef = extractBaseDataRef(node);
            }
            String defaultValueExpr = null;
            if ("text-box".equals(type)) {
                defaultValueExpr = extractDefaultValueExpr(node);
            }
            boolean required = extractRequired(node);
            JSONObject uniqueRule = extractUniqueRule(node);
            String uniqueGroup = null;
            if (uniqueRule != null) {
                JSONArray combine = uniqueRule.getJSONArray("combineFields");
                JSONArray group = new JSONArray();
                group.add(dbField.trim());
                if (combine != null) {
                    for (int i = 0; i < combine.size(); i++) {
                        String cf = combine.getString(i);
                        if (cf != null && !cf.trim().isEmpty() && !cf.trim().equals(dbField.trim())) {
                            group.add(cf.trim());
                        }
                    }
                }
                uniqueGroup = group.toJSONString();
            }
            fields.add(new FieldDef(dbField.trim(), dataType, sortNo[0]++, baseDataRef, defaultValueExpr,
                    required, uniqueRule != null, uniqueGroup));
        }
        JSONArray children = node.getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                collectFields(children.getJSONObject(i), fields, seen, sortNo);
            }
        }
    }

    /** 提取必填标记：props.required === true 或 validationRules 含 required 规则 */
    private boolean extractRequired(JSONObject node) {
        JSONObject props = node.getJSONObject("props");
        if (props == null) return false;
        if (Boolean.TRUE.equals(props.getBoolean("required"))) return true;
        JSONArray rules = props.getJSONArray("validationRules");
        if (rules != null) {
            for (int i = 0; i < rules.size(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                if (rule != null && "required".equals(rule.getString("type"))) return true;
            }
        }
        return false;
    }

    /** 提取唯一性规则（validationRules 中 type=='unique' 的规则，含 combineFields 组合字段） */
    private JSONObject extractUniqueRule(JSONObject node) {
        JSONObject props = node.getJSONObject("props");
        if (props == null) return null;
        JSONArray rules = props.getJSONArray("validationRules");
        if (rules == null) return null;
        for (int i = 0; i < rules.size(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            if (rule != null && "unique".equals(rule.getString("type"))) return rule;
        }
        return null;
    }

    /**
     * 编码规则列扩容为 TEXT（多段规则 JSON 可能超过 VARCHAR(512)，PG 下超长直接报错致规则丢失）。
     * 幂等：成功一次后跳过；按方言逐个尝试（PG/达梦/金仓 ALTER TYPE、Oracle/MySQL MODIFY、
     * SQLServer ALTER COLUMN；SQLite VARCHAR 无长度约束无需扩列，语法不支持则静默跳过）。
     */
    private void ensureExprColumnText() {
        if (exprColumnWidened) return;
        String[] ddls = {
                "ALTER TABLE lc_field_meta ALTER COLUMN default_value_expr TYPE TEXT",
                "ALTER TABLE lc_field_meta MODIFY default_value_expr TEXT",
                "ALTER TABLE lc_field_meta ALTER COLUMN default_value_expr varchar(max)",
        };
        MultiDataSourceHolder.setDatasource("lowcode");
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            for (String ddl : ddls) {
                try {
                    jdbc.execute(ddl);
                    exprColumnWidened = true;
                    log.info("[MetaSync] default_value_expr 已扩容为 TEXT: {}", ddl);
                    return;
                } catch (Exception ignore) {
                    // 该方言语法不适用，尝试下一条
                }
            }
            log.warn("[MetaSync] default_value_expr 扩容 DDL 全部不适用（SQLite 等无长度约束库可忽略）");
        } finally {
            MultiDataSourceHolder.clearDataSource();
        }
    }

    /** 提取编码规则 JSON（兼容 node 平铺层与 props 层） */
    private String extractDefaultValueExpr(JSONObject node) {        String expr = node.getString("defaultValueExpr");
        if (expr == null || expr.trim().isEmpty()) {
            JSONObject props = node.getJSONObject("props");
            expr = props != null ? props.getString("defaultValueExpr") : null;
        }
        String result = expr != null && !expr.trim().isEmpty() ? expr.trim() : null;
        log.info("[MetaSync] 提取编码规则: type={} dbField={} expr={}",
                node.getString("type"), node.getString("dbField"),
                result != null ? (result.length() > 60 ? result.substring(0, 60) + "...len=" + result.length() : result) : "null");
        return result;
    }


    private String extractBaseDataRef(JSONObject node) {
        String refPageId = node.getString("refPageId");
        if (refPageId == null || refPageId.isEmpty()) {
            JSONObject props = node.getJSONObject("props");
            refPageId = props != null ? props.getString("refPageId") : null;
        }
        if (refPageId == null || refPageId.isEmpty()) return null;
        JSONObject props = node.getJSONObject("props");
        String valueField = props != null ? props.getString("valueField") : null;
        String displayField = props != null ? props.getString("displayField") : null;
        // 编码字段：数据导入反查（编码→主键）的匹配字段，前端强制必配（兼容 node 平铺层与 props 层）
        String codeField = node.getString("codeField");
        if (codeField == null || codeField.isEmpty()) {
            codeField = props != null ? props.getString("codeField") : null;
        }
        if (valueField == null || valueField.isEmpty()) valueField = "id";
        if (displayField == null || displayField.isEmpty()) displayField = "name";
        JSONObject ref = new JSONObject();
        ref.put("refPageId", refPageId);
        ref.put("valueField", valueField);
        ref.put("displayField", displayField);
        if (codeField != null && !codeField.isEmpty()) {
            ref.put("codeField", codeField);
        }
        return ref.toJSONString();
    }

    private static class FieldDef {
        final String name;
        final String dataType;
        final int sortNo;
        String baseDataRef;
        String defaultValueExpr;
        final boolean required;
        final boolean unique;
        final String uniqueGroup;

        FieldDef(String name, String dataType, int sortNo, String baseDataRef, String defaultValueExpr,
                 boolean required, boolean unique, String uniqueGroup) {
            this.name = name;
            this.dataType = dataType;
            this.sortNo = sortNo;
            this.baseDataRef = baseDataRef;
            this.defaultValueExpr = defaultValueExpr;
            this.required = required;
            this.unique = unique;
            this.uniqueGroup = uniqueGroup;
        }
    }
}