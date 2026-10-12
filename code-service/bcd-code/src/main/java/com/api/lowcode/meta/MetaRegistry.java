package com.api.lowcode.meta;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.db.MultiDataSourceHolder;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class MetaRegistry {

    @Resource
    private SqlEngineJson helper;

    @Resource(name = "multiDataSource")
    private DataSource dataSource;

    private final ConcurrentMap<String, BizMeta> registry = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        ensurePrimaryKeyColumn();
        reload();
    }

    /** 确保 lc_biz_meta 表有 primary_key_field 列（兼容已建表的老数据库） */
    private void ensurePrimaryKeyColumn() {
        try {
            MultiDataSourceHolder.setDatasource("lowcode");
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("ALTER TABLE lc_biz_meta ADD COLUMN primary_key_field VARCHAR(128)");
            log.info("[MetaRegistry] lc_biz_meta 已添加 primary_key_field 列");
        } catch (Exception e) {
            // 列已存在或语法不兼容，忽略
        } finally {
            MultiDataSourceHolder.clearDataSource();
        }
    }

    public void reload() {
        registry.clear();
        try {
            JSONArray bizRows;
            try {
                String[] bizCols = { "id", "biz_code", "biz_name", "main_table", "main_table_alias",
                        "status_field", "logic_delete_field", "primary_key_field", "key_strategy", "tenant_id", "version", "remark" };
                bizRows = helper.query("lc_biz_meta", bizCols, null, "biz_code", false);
            } catch (Exception colErr) {
                log.warn("[MetaRegistry] key_strategy 列不存在，回退查询: {}", colErr.getMessage());
                String[] bizColsFallback = { "id", "biz_code", "biz_name", "main_table", "main_table_alias",
                        "status_field", "logic_delete_field", "primary_key_field", "tenant_id", "version", "remark" };
                bizRows = helper.query("lc_biz_meta", bizColsFallback, null, "biz_code", false);
            }
            for (int i = 0; i < bizRows.size(); i++) {
                JSONObject row = bizRows.getJSONObject(i);
                BizMeta meta = new BizMeta();
                meta.setId(row.getString("ID"));
                meta.setBizCode(row.getString("BIZ_CODE"));
                meta.setBizName(row.getString("BIZ_NAME"));
                meta.setMainTable(row.getString("MAIN_TABLE"));
                meta.setMainTableAlias(row.getString("MAIN_TABLE_ALIAS"));
                meta.setStatusField(row.getString("STATUS_FIELD"));
                meta.setLogicDeleteField(row.getString("LOGIC_DELETE_FIELD"));
                meta.setPrimaryKeyField(row.getString("PRIMARY_KEY_FIELD"));
                meta.setKeyStrategy(row.getString("KEY_STRATEGY"));
                meta.setTenantId(row.getString("TENANT_ID"));
                meta.setVersion(row.getInteger("VERSION"));
                meta.setRemark(row.getString("REMARK"));
                loadFields(meta);
                loadActions(meta);
                loadStateTransitions(meta);
                registry.put(meta.getBizCode(), meta);
            }
            log.info("[MetaRegistry] 加载完成，共 {} 个业务元数据: {}", registry.size(), registry.keySet());
        } catch (Exception e) {
            log.error("[MetaRegistry] 加载元数据失败", e);
        }
    }

    private void loadFields(BizMeta meta) throws Exception {
        String[] cols = { "id", "biz_code", "field_name", "column_type", "data_type",
                "is_required", "is_unique", "is_system_field", "permission_expr",
                "default_value_expr", "validation_expr", "sort_no" };
        JSONArray rows = helper.query("lc_field_meta", cols, helper.eq("biz_code", meta.getBizCode()), "sort_no", false);
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            FieldMeta f = new FieldMeta();
            f.setId(row.getString("ID"));
            f.setBizCode(row.getString("BIZ_CODE"));
            f.setFieldName(row.getString("FIELD_NAME"));
            f.setColumnType(row.getString("COLUMN_TYPE"));
            f.setDataType(row.getString("DATA_TYPE"));
            f.setIsRequired(row.getBoolean("IS_REQUIRED"));
            f.setIsUnique(row.getBoolean("IS_UNIQUE"));
            f.setIsSystemField(row.getBoolean("IS_SYSTEM_FIELD"));
            f.setPermissionExpr(row.getString("PERMISSION_EXPR"));
            f.setDefaultValueExpr(row.getString("DEFAULT_VALUE_EXPR"));
            f.setValidationExpr(row.getString("VALIDATION_EXPR"));
            f.setSortNo(row.getInteger("SORT_NO"));
            meta.getFields().add(f);
        }
    }

    private void loadActions(BizMeta meta) throws Exception {
        String[] cols = { "id", "biz_code", "action", "strategy_type", "pre_hook_plugins",
                "post_hook_plugins", "status_transition", "data_scope_expr", "enabled",
                "ext_api_url", "ext_api_method", "ext_data_path", "ext_total_path" };
        JSONArray rows = helper.query("lc_action_meta", cols, helper.eq("biz_code", meta.getBizCode()), null, false);
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            ActionMeta a = new ActionMeta();
            a.setId(row.getString("ID"));
            a.setBizCode(row.getString("BIZ_CODE"));
            a.setAction(row.getString("ACTION"));
            a.setStrategyType(row.getString("STRATEGY_TYPE"));
            a.setPreHookPlugins(row.getString("PRE_HOOK_PLUGINS"));
            a.setPostHookPlugins(row.getString("POST_HOOK_PLUGINS"));
            a.setStatusTransition(row.getString("STATUS_TRANSITION"));
            a.setDataScopeExpr(row.getString("DATA_SCOPE_EXPR"));
            a.setEnabled(row.getBoolean("ENABLED"));
            a.setExtApiUrl(row.getString("EXT_API_URL"));
            a.setExtApiMethod(row.getString("EXT_API_METHOD"));
            a.setExtDataPath(row.getString("EXT_DATA_PATH"));
            a.setExtTotalPath(row.getString("EXT_TOTAL_PATH"));
            meta.getActions().put(a.getAction(), a);
        }
    }

    private void loadStateTransitions(BizMeta meta) throws Exception {
        String[] cols = { "id", "biz_code", "current_state", "next_state", "event", "guard_expr", "action", "enabled" };
        JSONArray rows = helper.query("lc_state_machine", cols, helper.eq("biz_code", meta.getBizCode()), null, false);
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            StateMachineMeta s = new StateMachineMeta();
            s.setId(row.getString("ID"));
            s.setBizCode(row.getString("BIZ_CODE"));
            s.setCurrentState(row.getString("CURRENT_STATE"));
            s.setNextState(row.getString("NEXT_STATE"));
            s.setEvent(row.getString("EVENT"));
            s.setGuardExpr(row.getString("GUARD_EXPR"));
            s.setAction(row.getString("ACTION"));
            s.setEnabled(row.getBoolean("ENABLED"));
            meta.getStateTransitions().add(s);
        }
    }

    public BizMeta getMeta(String bizCode) {
        return registry.get(bizCode);
    }

    public boolean contains(String bizCode) {
        return registry.containsKey(bizCode);
    }
}