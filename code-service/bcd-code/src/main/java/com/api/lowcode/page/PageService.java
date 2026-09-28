package com.api.lowcode.page;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.SqlEngineJson;
import com.api.lowcode.common.BusinessTableSyncService;
import com.api.lowcode.perm.PagePermService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;

@Slf4j
@Service
public class PageService {

  @Resource
  private SqlEngineJson helper;

  @Resource
  private PagePermService pagePermService;

  @Resource
  private BusinessTableSyncService tableSyncService;

  private static final String TABLE = "lowcode_page";

  private static final String[][] LIST_MAPPING = {
      { "page_id", "pageId" }, { "page_name", "pageName" }, { "system_id", "systemId" }, { "module_id", "moduleId" },
      { "page_type", "pageType" }, { "saved_at", "savedAt" }, { "enabled", "enabled" },
      { "deleted_at", "deletedAt" }
  };
  private static final String[][] ALL_MAPPING = {
      { "page_id", "pageId" }, { "page_name", "pageName" }, { "system_id", "systemId" }, { "module_id", "moduleId" },
      { "page_type", "pageType" }, { "saved_at", "savedAt" }, { "enabled", "enabled" },
      { "deleted_at", "deletedAt" }, { "schema_json", "schemaJson" }
  };

  public JSONArray listPages(boolean deleted) throws Exception {
    String[] cols = { "page_id", "page_name", "system_id", "module_id", "page_type", "saved_at", "enabled",
        "deleted_at" };
    return helper.queryAndMap(TABLE, cols, helper.eq("deleted", deleted), "saved_at", true, LIST_MAPPING);
  }

  public JSONObject getPage(String pageId) throws Exception {
    String[] cols = { "schema_json" };
    JSONArray rows = helper.query(TABLE, cols, helper.eq("page_id", pageId), null, false);
    if (rows.isEmpty()) {
      return null;
    }
    String json = rows.getJSONObject(0).getString("SCHEMA_JSON");
    return json != null ? JSONObject.parseObject(json) : null;
  }

  public JSONObject savePage(JSONObject schema) throws Exception {
    String pageId = schema.getString("pageId");
    String now = Instant.now().toString();
    Boolean enabledObj = schema.getBoolean("enabled");
    boolean enabled = enabledObj == null || enabledObj;
    String deletedAt = schema.getString("deletedAt");

    pagePermService.ensurePermCodes(schema);

    JSONObject data = new JSONObject();
    data.put("page_id", pageId);
    data.put("page_name", schema.getString("pageName"));
    data.put("table_name", schema.getString("tableName"));
    data.put("system_id", schema.getString("systemId"));
    data.put("module_id", schema.getString("moduleId"));
    data.put("page_type", schema.getString("pageType"));
    data.put("schema_json", schema.toJSONString());
    data.put("enabled", enabled);
    data.put("deleted", deletedAt != null);
    data.put("deleted_at", deletedAt);
    data.put("saved_at", now);
    data.put("updated_at", now);

    SqlEngineJson.check(helper.upsert(TABLE, data, helper.eq("page_id", pageId)));

    pagePermService.syncPerms(pageId, schema);

    // 自动检测并创建业务表及字段（仅表单模式保存时触发，表格/移动端不涉及建表）
    String lastBizMode = schema.getString("lastBizMode");
    String syncMsg = null;
    if (lastBizMode == null || "form".equals(lastBizMode)) {
      syncMsg = tableSyncService.syncTable(schema);
      // 同步字段关联（dx-base-data 控件关联的基础资料页面）
      syncFieldRefs(pageId, schema);
    }

    // 记录保存日志
    try {
      JSONObject logData = new JSONObject();
      logData.put("page_id", pageId);
      logData.put("page_name", schema.getString("pageName"));
      logData.put("table_name", schema.getString("tableName"));
      logData.put("page_type", schema.getString("pageType"));
      logData.put("action", "save");
      logData.put("table_synced", syncMsg != null);
      logData.put("table_sync_msg", syncMsg != null ? syncMsg.substring(0, Math.min(512, syncMsg.length())) : null);
      logData.put("schema_size", schema.toJSONString().length());
      logData.put("saved_at", now);
      SqlEngineJson.check(helper.insert("lowcode_page_save_log", logData));
    } catch (Exception logEx) {
      log.warn("保存日志记录失败（不影响保存）: {}", logEx.getMessage());
    }

    log.info("[PageSave] pageId={}, pageName={}, pageType={}, tableSync={}", pageId, schema.getString("pageName"), schema.getString("pageType"), syncMsg);

    // 返回保存结果（含建表同步消息）
    JSONObject result = new JSONObject();
    result.put("pageId", pageId);
    result.put("savedAt", now);
    result.put("tableSyncMsg", syncMsg);
    return result;
  }

  public void deletePage(String pageId, boolean hard) throws Exception {
    if (hard) {
      SqlEngineJson.check(helper.delete(TABLE, helper.eq("page_id", pageId)));
      pagePermService.deleteByPage(pageId);
    } else {
      String now = Instant.now().toString();
      JSONObject data = new JSONObject();
      data.put("deleted", true);
      data.put("deleted_at", now);
      data.put("saved_at", now);
      data.put("updated_at", now);
      SqlEngineJson.check(helper.update(TABLE, data, helper.eq("page_id", pageId)));
    }
  }

  public void setPageEnabled(String pageId, boolean enabled) throws Exception {
    String now = Instant.now().toString();
    JSONObject data = new JSONObject();
    data.put("enabled", enabled);
    data.put("saved_at", now);
    data.put("updated_at", now);
    SqlEngineJson.check(helper.update(TABLE, data, helper.eq("page_id", pageId)));
  }

  public void restorePage(String pageId) throws Exception {
    String now = Instant.now().toString();
    JSONObject data = new JSONObject();
    data.put("deleted", false);
    data.put("deleted_at", null);
    data.put("saved_at", now);
    data.put("updated_at", now);
    SqlEngineJson.check(helper.update(TABLE, data, helper.eq("page_id", pageId)));
  }

  public JSONArray allPages() throws Exception {
    String[] cols = { "page_id", "page_name", "system_id", "module_id", "page_type", "saved_at", "enabled",
        "deleted_at",
        "schema_json" };
    return helper.queryAndMap(TABLE, cols, null, null, false, ALL_MAPPING);
  }

  /** 同步字段关联：扫描 schema 中的 dx-base-data 控件，记录到 lowcode_field_ref 表 */
  private void syncFieldRefs(String pageId, JSONObject schema) {
    try {
      SqlEngineJson.check(helper.delete("lowcode_field_ref", helper.eq("source_page_id", pageId)));
      JSONArray root = schema.getJSONArray("root");
      if (root != null) {
        collectAndInsertFieldRefs(pageId, root);
      }
      JSONObject bizSchemas = schema.getJSONObject("bizSchemas");
      if (bizSchemas != null) {
        for (String mode : new String[] { "form", "table", "mobile" }) {
          JSONArray arr = bizSchemas.getJSONArray(mode);
          if (arr != null) {
            collectAndInsertFieldRefs(pageId, arr);
          }
        }
      }
    } catch (Exception e) {
      log.warn("同步字段关联失败（不影响保存）: {}", e.getMessage());
    }
  }

  /** 递归扫描节点数组，提取 dx-base-data 控件关联信息并插入 */
  private void collectAndInsertFieldRefs(String pageId, JSONArray nodes) {
    if (nodes == null) return;
    for (int i = 0; i < nodes.size(); i++) {
      JSONObject node = nodes.getJSONObject(i);
      String type = node.getString("type");
      if ("dx-base-data".equals(type)) {
        try {
          JSONObject refData = new JSONObject();
          refData.put("source_page_id", pageId);
          refData.put("source_field_name", node.getString("dbField"));
          JSONObject props = node.getJSONObject("props");
          if (props != null) {
            refData.put("target_page_id", props.getString("refPageId"));
            refData.put("display_field", props.getString("displayField"));
            refData.put("value_field", props.getString("valueField"));
          }
          refData.put("updated_at", Instant.now().toString());
          SqlEngineJson.check(helper.insert("lowcode_field_ref", refData));
        } catch (Exception e) {
          log.warn("插入字段关联失败: {}", e.getMessage());
        }
      }
      JSONArray children = node.getJSONArray("children");
      if (children != null) {
        collectAndInsertFieldRefs(pageId, children);
      }
    }
  }
}
