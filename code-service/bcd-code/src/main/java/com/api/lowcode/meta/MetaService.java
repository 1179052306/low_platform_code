package com.api.lowcode.meta;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class MetaService {

    @Resource
    private SqlEngineJson helper;

    @Resource
    private MetaRegistry metaRegistry;

    private void reloadCache() {
        metaRegistry.reload();
    }

    // ==================== 业务元数据 ====================

    public JSONArray listBizMeta() throws Exception {
        String[] cols = { "id", "biz_code", "biz_name", "main_table", "main_table_alias",
                "status_field", "logic_delete_field", "version", "remark" };
        String[][] mapping = {
            {"id", "id"}, {"biz_code", "bizCode"}, {"biz_name", "bizName"},
            {"main_table", "mainTable"}, {"main_table_alias", "mainTableAlias"},
            {"status_field", "statusField"}, {"logic_delete_field", "logicDeleteField"},
            {"version", "version"}, {"remark", "remark"}
        };
        return helper.queryAndMap("lc_biz_meta", cols, null, "biz_code", false, mapping);
    }

    public JSONArray getBizMeta(String bizCode) throws Exception {
        String[] cols = { "id", "biz_code", "biz_name", "main_table", "main_table_alias",
                "status_field", "logic_delete_field", "version", "remark" };
        String[][] mapping = {
            {"id", "id"}, {"biz_code", "bizCode"}, {"biz_name", "bizName"},
            {"main_table", "mainTable"}, {"main_table_alias", "mainTableAlias"},
            {"status_field", "statusField"}, {"logic_delete_field", "logicDeleteField"},
            {"version", "version"}, {"remark", "remark"}
        };
        return helper.queryAndMap("lc_biz_meta", cols, helper.eq("biz_code", bizCode), null, false, mapping);
    }

    public void saveBizMeta(JSONObject data) throws Exception {
        if (data.getString("id") == null) {
            data.put("id", UUID.randomUUID().toString().replace("-", ""));
        }
        data.put("updated_at", Instant.now().toString());
        if (data.getString("created_at") == null) {
            data.put("created_at", Instant.now().toString());
        }
        ResMsg r = helper.upsert("lc_biz_meta", data, helper.eq("biz_code", data.getString("biz_code")));
        SqlEngineJson.check(r);
        reloadCache();
        log.info("[MetaService] 保存业务元数据: {}", data.getString("biz_code"));
    }

    public void deleteBizMeta(String bizCode) throws Exception {
        ResMsg r = helper.delete("lc_biz_meta", helper.eq("biz_code", bizCode));
        SqlEngineJson.check(r);
        reloadCache();
        log.info("[MetaService] 删除业务元数据: {}", bizCode);
    }

    // ==================== 字段元数据 ====================

    public JSONArray listFieldMeta(String bizCode) throws Exception {
        String[] cols = { "id", "biz_code", "field_name", "column_type", "data_type",
                "is_required", "is_unique", "is_system_field", "permission_expr",
                "default_value_expr", "validation_expr", "sort_no" };
        String[][] mapping = {
            {"id", "id"}, {"biz_code", "bizCode"}, {"field_name", "fieldName"},
            {"column_type", "columnType"}, {"data_type", "dataType"},
            {"is_required", "isRequired"}, {"is_unique", "isUnique"},
            {"is_system_field", "isSystemField"}, {"permission_expr", "permissionExpr"},
            {"default_value_expr", "defaultValueExpr"}, {"validation_expr", "validationExpr"},
            {"sort_no", "sortNo"}
        };
        return helper.queryAndMap("lc_field_meta", cols, helper.eq("biz_code", bizCode), "sort_no", false, mapping);
    }

    public void saveFieldMeta(JSONObject data) throws Exception {
        if (data.getString("id") == null) {
            data.put("id", UUID.randomUUID().toString().replace("-", ""));
        }
        data.put("created_at", Instant.now().toString());
        ResMsg r = helper.upsert("lc_field_meta", data, helper.eq("id", data.getString("id")));
        SqlEngineJson.check(r);
        reloadCache();
        log.info("[MetaService] 保存字段元数据: {} {}", data.getString("biz_code"), data.getString("field_name"));
    }

    public void deleteFieldMeta(String id) throws Exception {
        ResMsg r = helper.delete("lc_field_meta", helper.eq("id", id));
        SqlEngineJson.check(r);
        reloadCache();
        log.info("[MetaService] 删除字段元数据: {}", id);
    }

    // ==================== 动作元数据 ====================

    public JSONArray listActionMeta(String bizCode) throws Exception {
        String[] cols = { "id", "biz_code", "action", "strategy_type", "pre_hook_plugins",
                "post_hook_plugins", "status_transition", "data_scope_expr", "enabled" };
        String[][] mapping = {
            {"id", "id"}, {"biz_code", "bizCode"}, {"action", "action"},
            {"strategy_type", "strategyType"}, {"pre_hook_plugins", "preHookPlugins"},
            {"post_hook_plugins", "postHookPlugins"}, {"status_transition", "statusTransition"},
            {"data_scope_expr", "dataScopeExpr"}, {"enabled", "enabled"}
        };
        return helper.queryAndMap("lc_action_meta", cols, helper.eq("biz_code", bizCode), null, false, mapping);
    }

    public void saveActionMeta(JSONObject data) throws Exception {
        if (data.getString("id") == null) {
            data.put("id", UUID.randomUUID().toString().replace("-", ""));
        }
        data.put("created_at", Instant.now().toString());
        ResMsg r = helper.upsert("lc_action_meta", data, helper.eq("id", data.getString("id")));
        SqlEngineJson.check(r);
        reloadCache();
        log.info("[MetaService] 保存动作元数据: {} {}", data.getString("biz_code"), data.getString("action"));
    }

    public void deleteActionMeta(String id) throws Exception {
        ResMsg r = helper.delete("lc_action_meta", helper.eq("id", id));
        SqlEngineJson.check(r);
        reloadCache();
        log.info("[MetaService] 删除动作元数据: {}", id);
    }

    // ==================== 状态机元数据 ====================

    public JSONArray listStateMachine(String bizCode) throws Exception {
        String[] cols = { "id", "biz_code", "current_state", "next_state", "event", "guard_expr", "action", "enabled" };
        String[][] mapping = {
            {"id", "id"}, {"biz_code", "bizCode"}, {"current_state", "currentState"},
            {"next_state", "nextState"}, {"event", "event"}, {"guard_expr", "guardExpr"},
            {"action", "action"}, {"enabled", "enabled"}
        };
        return helper.queryAndMap("lc_state_machine", cols, helper.eq("biz_code", bizCode), null, false, mapping);
    }

    public void saveStateMachine(JSONObject data) throws Exception {
        if (data.getString("id") == null) {
            data.put("id", UUID.randomUUID().toString().replace("-", ""));
        }
        data.put("created_at", Instant.now().toString());
        ResMsg r = helper.upsert("lc_state_machine", data, helper.eq("id", data.getString("id")));
        SqlEngineJson.check(r);
        reloadCache();
        log.info("[MetaService] 保存状态机: {} {}", data.getString("biz_code"), data.getString("action"));
    }

    public void deleteStateMachine(String id) throws Exception {
        ResMsg r = helper.delete("lc_state_machine", helper.eq("id", id));
        SqlEngineJson.check(r);
        reloadCache();
        log.info("[MetaService] 删除状态机: {}", id);
    }

    // ==================== 分层继承：合并 schema ====================

    public JSONObject getMergedSchema(String pageId) throws Exception {
        String[] cols = { "page_id", "page_name", "table_name", "page_css", "theme_id", "root", "page_type", "biz_schemas", "last_biz_mode", "layer_type", "parent_page_id" };
        String[][] mapping = {
            {"page_id", "page_id"}, {"page_name", "page_name"}, {"table_name", "table_name"},
            {"page_css", "page_css"}, {"theme_id", "theme_id"}, {"root", "root"},
            {"page_type", "page_type"}, {"biz_schemas", "biz_schemas"}, {"last_biz_mode", "last_biz_mode"},
            {"layer_type", "layer_type"}, {"parent_page_id", "parent_page_id"}
        };
        JSONArray pages = helper.queryAndMap("lowcode_page", cols, helper.eq("page_id", pageId), null, false, mapping);
        if (pages.isEmpty()) {
            throw new Exception("页面不存在: " + pageId);
        }
        JSONObject page = pages.getJSONObject(0);
        String parentPageId = page.getString("parent_page_id");
        if (parentPageId != null && !parentPageId.isEmpty()) {
            JSONObject parent = getMergedSchema(parentPageId);
            return mergeSchema(parent, page);
        }
        return page;
    }

    private JSONObject mergeSchema(JSONObject parent, JSONObject child) {
        JSONObject merged = new JSONObject(parent);
        JSONObject childRoot = child.getJSONObject("root");
        if (childRoot != null) {
            merged.put("root", childRoot);
        }
        String childName = child.getString("page_name");
        if (childName != null && !childName.isEmpty()) {
            merged.put("page_name", childName);
        }
        Object childBizSchemas = child.get("biz_schemas");
        if (childBizSchemas != null) {
            merged.put("biz_schemas", childBizSchemas);
        }
        Object childLastBizMode = child.get("last_biz_mode");
        if (childLastBizMode != null) {
            merged.put("last_biz_mode", childLastBizMode);
        }
        Object childPageType = child.get("page_type");
        if (childPageType != null) {
            merged.put("page_type", childPageType);
        }
        return merged;
    }

    // ==================== 版本化 ====================

    public JSONArray getVersionHistory(String metaKey) throws Exception {
        String[] cols = { "id", "meta_key", "version_no", "content", "operator", "remark", "created_at" };
        String[][] mapping = {
            {"id", "versionId"}, {"meta_key", "metaKey"}, {"version_no", "versionNo"},
            {"content", "content"}, {"operator", "createdBy"}, {"remark", "changeLog"},
            {"created_at", "createdAt"}
        };
        return helper.queryAndMap("lc_meta_version", cols, helper.eq("meta_key", metaKey), "version_no", true, mapping);
    }

    public JSONObject getVersionSchema(String pageId, String versionId) throws Exception {
        JSONArray versions = helper.query("lc_meta_version",
                new String[]{ "content" }, helper.eq("id", versionId), null, false);
        if (versions.isEmpty()) {
            throw new Exception("版本不存在: " + versionId);
        }
        String contentStr = versions.getJSONObject(0).getString("CONTENT");
        if (contentStr == null || contentStr.isEmpty()) {
            throw new Exception("版本内容为空");
        }
        return JSONObject.parse(contentStr);
    }

    public void rollback(String metaKey, String versionId) throws Exception {
        JSONArray versions = helper.query("lc_meta_version",
                new String[]{ "content" }, helper.eq("id", versionId), null, false);
        if (versions.isEmpty()) {
            throw new Exception("版本不存在: " + versionId);
        }
        String content = versions.getJSONObject(0).getString("CONTENT");
        JSONObject data = new JSONObject();
        data.put("meta_key", metaKey);
        data.put("version_no", getNextVersionNo(metaKey));
        data.put("content", content);
        data.put("operator", "system");
        data.put("remark", "rollback to " + versionId);
        data.put("id", UUID.randomUUID().toString().replace("-", ""));
        data.put("created_at", Instant.now().toString());
        ResMsg r = helper.insert("lc_meta_version", data);
        SqlEngineJson.check(r);
        log.info("[MetaService] 回滚: {} -> {}", metaKey, versionId);
    }

    public JSONArray checkBreaking(String metaKey, JSONObject newContent) throws Exception {
        JSONArray result = new JSONArray();
        JSONArray history = getVersionHistory(metaKey);
        if (history.isEmpty()) {
            return result;
        }
        JSONObject latest = history.getJSONObject(history.size() - 1);
        String oldContentStr = latest.getString("content");
        if (oldContentStr == null) {
            return result;
        }
        JSONObject oldContent = JSONObject.parse(oldContentStr);
        JSONObject oldFields = oldContent.getJSONObject("fields");
        JSONObject newFields = newContent.getJSONObject("fields");
        if (oldFields != null && newFields != null) {
            for (String field : oldFields.keySet()) {
                if (!newFields.containsKey(field)) {
                    JSONObject breaking = new JSONObject();
                    breaking.put("type", "FIELD_REMOVED");
                    breaking.put("field", field);
                    breaking.put("severity", "HIGH");
                    result.add(breaking);
                }
            }
        }
        return result;
    }

    public void recordVersion(String metaKey, JSONObject content) throws Exception {
        JSONObject data = new JSONObject();
        data.put("id", UUID.randomUUID().toString().replace("-", ""));
        data.put("meta_key", metaKey);
        data.put("version_no", getNextVersionNo(metaKey));
        data.put("content", content.toJSONString());
        data.put("operator", "system");
        data.put("remark", "save");
        data.put("created_at", Instant.now().toString());
        SqlEngineJson.check(helper.insert("lc_meta_version", data));
    }

    private int getNextVersionNo(String metaKey) throws Exception {
        JSONArray versions = getVersionHistory(metaKey);
        if (versions.isEmpty()) return 1;
        int max = 0;
        for (int i = 0; i < versions.size(); i++) {
            int no = versions.getJSONObject(i).getIntValue("versionNo");
            if (no > max) max = no;
        }
        return max + 1;
    }

    // ==================== 元数据注册表（MetaManageView） ====================

    public JSONArray listMetaRegistry(JSONObject filter) throws Exception {
        String[] cols = { "id", "domain", "meta_type", "meta_key", "meta_name", "content", "version_no", "enabled", "updated_at", "page_id", "biz_code" };
        String[][] mapping = {
            {"id", "id"}, {"domain", "domain"}, {"meta_type", "metaType"},
            {"meta_key", "metaKey"}, {"meta_name", "metaName"}, {"content", "content"},
            {"version_no", "versionNo"}, {"enabled", "enabled"}, {"updated_at", "updatedAt"},
            {"page_id", "pageId"}, {"biz_code", "bizCode"}
        };
        JSONArray conditions = new JSONArray();
        String domain = filter != null ? filter.getString("domain") : null;
        String metaType = filter != null ? filter.getString("metaType") : null;
        if (domain != null && !domain.isEmpty()) {
            JSONArray cond = helper.eq("domain", domain);
            for (int i = 1; i < cond.size(); i++) conditions.add(cond.get(i));
        }
        if (metaType != null && !metaType.isEmpty()) {
            JSONArray cond = helper.eq("meta_type", metaType);
            for (int i = 1; i < cond.size(); i++) conditions.add(cond.get(i));
        }
        return helper.queryAndMap("lc_meta_registry", cols, conditions, "updated_at", true, mapping);
    }

    public void saveMetaRegistry(JSONObject item) throws Exception {
        JSONObject data = new JSONObject();
        String id = item.getString("id");
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString().replace("-", "");
        }
        data.put("id", id);
        data.put("domain", item.getString("domain"));
        data.put("meta_type", item.getString("metaType"));
        data.put("meta_key", item.getString("metaKey"));
        data.put("meta_name", item.getString("metaName"));
        data.put("content", item.containsKey("content") ? JSON.toJSONString(item.get("content")) : null);
        data.put("version_no", item.containsKey("version") ? item.getIntValue("version") : 1);
        data.put("enabled", item.containsKey("enabled") ? item.getBooleanValue("enabled") : true);
        data.put("updated_at", Instant.now().toString());
        data.put("page_id", item.getString("pageId"));
        data.put("biz_code", item.getString("bizCode"));
        helper.upsert("lc_meta_registry", data, helper.eq("id", id));
    }

    public void deleteMetaRegistry(String id) throws Exception {
        helper.delete("lc_meta_registry", helper.eq("id", id));
    }
}