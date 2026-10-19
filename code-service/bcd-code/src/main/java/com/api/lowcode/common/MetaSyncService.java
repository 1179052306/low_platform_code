package com.api.lowcode.common;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.meta.MetaRegistry;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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

    private static final Map<String, String> TYPE_MAP = new HashMap<>();

    static {
        TYPE_MAP.put("text-box", "VARCHAR");
        TYPE_MAP.put("text-area", "TEXT");
        TYPE_MAP.put("number-box", "DECIMAL");
        TYPE_MAP.put("date-box", "TIMESTAMP");
        TYPE_MAP.put("check-box", "BOOLEAN");
        TYPE_MAP.put("switch", "BOOLEAN");
        TYPE_MAP.put("select-box", "VARCHAR");
        TYPE_MAP.put("color-box", "VARCHAR");
        TYPE_MAP.put("tag-box", "VARCHAR");
        TYPE_MAP.put("lookup", "VARCHAR");
        TYPE_MAP.put("radio-group", "VARCHAR");
        TYPE_MAP.put("slider", "DECIMAL");
        TYPE_MAP.put("range-slider", "VARCHAR");
        TYPE_MAP.put("progress-bar", "DECIMAL");
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
            String pkField = schema.getString("primaryKeyField");
            bizMeta.put("primary_key_field", pkField != null && !pkField.trim().isEmpty() ? pkField.trim() : "id");
            String keyStrategy = schema.getString("keyStrategy");
            bizMeta.put("key_strategy", keyStrategy != null && !keyStrategy.trim().isEmpty() ? keyStrategy.trim() : "SNOWFLAKE");
            bizMeta.put("version", 1);
            bizMeta.put("remark", "由页面保存自动生成");
            bizMeta.put("created_at", now);
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
                fieldMeta.put("is_required", false);
                fieldMeta.put("is_unique", false);
                fieldMeta.put("is_system_field", false);
                fieldMeta.put("sort_no", f.sortNo);
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
        } catch (Exception e) {
            log.error("[MetaSync] 同步元数据失败: bizCode={}, {}", bizCode, e.getMessage(), e);
        }
    }

    /** 从表格列配置中扫描 baseDataRef，合并到对应字段的 displayField */
    private void mergeTableBaseDataRefs(JSONObject schema, List<FieldDef> fields) {
        JSONObject bizSchemas = schema.getJSONObject("bizSchemas");
        if (bizSchemas == null) return;
        JSONArray tableRoot = bizSchemas.getJSONArray("table");
        if (tableRoot == null) return;
        Map<String, java.util.Set<String>> tableDisplayFields = new HashMap<>();
        scanTableColumns(tableRoot, tableDisplayFields);
        if (tableDisplayFields.isEmpty()) return;
        for (FieldDef f : fields) {
            if (f.baseDataRef == null) continue;
            java.util.Set<String> extraFields = tableDisplayFields.get(f.name.toUpperCase());
            if (extraFields == null || extraFields.isEmpty()) continue;
            try {
                JSONObject ref = JSONObject.parseObject(f.baseDataRef);
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
                f.baseDataRef = ref.toJSONString();
            } catch (Exception e) {
                log.warn("[MetaSync] 合并表格 baseDataRef 失败: field={}", f.name);
            }
        }
    }

    private void scanTableColumns(JSONArray nodes, Map<String, java.util.Set<String>> map) {
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
                        if (dataField != null && baseDataRef != null && dataField.contains(".")) {
                            String sourceField = baseDataRef.getString("sourceField");
                            String displayField = dataField.substring(dataField.indexOf(".") + 1);
                            if (sourceField != null && !displayField.isEmpty()) {
                                map.computeIfAbsent(sourceField.toUpperCase(), k -> new java.util.LinkedHashSet<>()).add(displayField);
                            }
                        }
                    }
                }
            }
            JSONArray children = node.getJSONArray("children");
            if (children != null) scanTableColumns(children, map);
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
            fields.add(new FieldDef(dbField.trim(), dataType, sortNo[0]++, baseDataRef));
        }
        JSONArray children = node.getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                collectFields(children.getJSONObject(i), fields, seen, sortNo);
            }
        }
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
        if (valueField == null || valueField.isEmpty()) valueField = "id";
        if (displayField == null || displayField.isEmpty()) displayField = "name";
        JSONObject ref = new JSONObject();
        ref.put("refPageId", refPageId);
        ref.put("valueField", valueField);
        ref.put("displayField", displayField);
        return ref.toJSONString();
    }

    private static class FieldDef {
        final String name;
        final String dataType;
        final int sortNo;
        String baseDataRef;

        FieldDef(String name, String dataType, int sortNo, String baseDataRef) {
            this.name = name;
            this.dataType = dataType;
            this.sortNo = sortNo;
            this.baseDataRef = baseDataRef;
        }
    }
}