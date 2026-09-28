package com.api.lowcode.perm;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;

/**
 * 页面字段/按钮权限码服务 —— 从页面 schema 中提取 permCode 独立存储到 lowcode_page_perm 表。
 *
 * <p>
 * 保存页面时自动调用 {@link #syncPerms} 提取 schema 中所有 column/toolbarItem 的 permCode，
 * 删除旧记录后批量插入，保持与 schema_json 同步。
 * </p>
 */
@Slf4j
@Service
public class PagePermService {

  @Resource
  private SqlEngineJson helper;

  private static final String TABLE = "lowcode_page_perm";

  private static final String[][] MAPPING = {
      { "id", "id" }, { "page_id", "pageId" }, { "perm_type", "permType" },
      { "item_key", "itemKey" }, { "item_label", "itemLabel" }, { "perm_code", "permCode" },
      { "sort_order", "sortOrder" }, { "updated_at", "updatedAt" }
  };

  /**
   * 从页面 schema 中提取所有 permCode 并同步到数据库。
   * 先删除该页面的旧权限记录，再批量插入新记录。
   */
  public void syncPerms(String pageId, JSONObject schema) throws Exception {
    SqlEngineJson.check(helper.delete(TABLE, helper.eq("page_id", pageId)));

    JSONArray root = schema.getJSONArray("root");
    if (root == null) {
      return;
    }

    String now = Instant.now().toString();
    int sortOrder = 0;

    for (int i = 0; i < root.size(); i++) {
      sortOrder = extractAndSave(root.getJSONObject(i), pageId, now, sortOrder);
    }
  }

  /**
   * 遍历 schema 组件树，给所有缺失的 permCode 补充默认值。
   * 字段默认 = dataField，工具栏按钮默认 = name，普通按钮默认 = text。
   * 保存前调用，确保 schema_json 本身也带 permCode。
   */
  public void ensurePermCodes(JSONObject schema) {
    JSONArray root = schema.getJSONArray("root");
    if (root == null) {
      return;
    }
    for (int i = 0; i < root.size(); i++) {
      ensureNodePerm(root.getJSONObject(i));
    }
  }

  private void ensureNodePerm(JSONObject node) {
    if (node == null) {
      return;
    }
    JSONObject props = node.getJSONObject("props");
    if (props != null) {
      ensureColumnsPerm(props);
      ensureToolbarItemsPerm(props);
      ensureButtonPerm(props);
    }
    JSONArray children = node.getJSONArray("children");
    if (children != null) {
      for (int i = 0; i < children.size(); i++) {
        ensureNodePerm(children.getJSONObject(i));
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void ensureColumnsPerm(JSONObject props) {
    JSONArray columns = resolveArray(props, "columns");
    if (columns == null) {
      return;
    }
    for (int i = 0; i < columns.size(); i++) {
      JSONObject col = columns.getJSONObject(i);
      String permCode = col.getString("permCode");
      if (permCode == null || permCode.isEmpty()) {
        col.put("permCode", col.getString("dataField"));
      }
    }
    props.put("columns", columns);
  }

  @SuppressWarnings("unchecked")
  private void ensureToolbarItemsPerm(JSONObject props) {
    JSONArray items = null;
    JSONObject toolbar = props.getJSONObject("toolbar");
    if (toolbar != null) {
      items = resolveArray(toolbar, "items");
    }
    if (items == null) {
      items = resolveArray(props, "toolbarItems");
    }
    if (items == null) {
      return;
    }
    for (int i = 0; i < items.size(); i++) {
      JSONObject item = items.getJSONObject(i);
      String permCode = item.getString("permCode");
      if (permCode == null || permCode.isEmpty()) {
        item.put("permCode", item.getString("name"));
      }
    }
    if (toolbar != null) {
      toolbar.put("items", items);
    } else {
      props.put("toolbarItems", items);
    }
  }

  @SuppressWarnings("unchecked")
  private void ensureButtonPerm(JSONObject props) {
    String permCode = props.getString("permCode");
    if (permCode == null || permCode.isEmpty()) {
      String text = props.getString("text");
      if (text != null && !text.isEmpty()) {
        props.put("permCode", text);
      }
    }
  }

  /**
   * 递归遍历组件树，从 props.columns 和 props.toolbarItems 中提取 permCode。
   *
   * @return 更新后的 sortOrder
   */
  private int extractAndSave(JSONObject node, String pageId, String now, int sortOrder) throws Exception {
    if (node == null) {
      return sortOrder;
    }

    JSONObject props = node.getJSONObject("props");
    if (props != null) {
      sortOrder = extractFromColumns(props, pageId, now, sortOrder);
      sortOrder = extractFromToolbarItems(props, pageId, now, sortOrder);
      sortOrder = extractFromNodePerm(props, node.getString("type"), pageId, now, sortOrder);
    }

    JSONArray children = node.getJSONArray("children");
    if (children != null) {
      for (int i = 0; i < children.size(); i++) {
        sortOrder = extractAndSave(children.getJSONObject(i), pageId, now, sortOrder);
      }
    }

    return sortOrder;
  }

  /** 从 props.columns 提取字段权限码（支持 JSONArray 和 JSON 字符串两种存储格式） */
  private int extractFromColumns(JSONObject props, String pageId, String now, int sortOrder) throws Exception {
    JSONArray columns = resolveArray(props, "columns");
    if (columns == null) {
      return sortOrder;
    }
    for (int i = 0; i < columns.size(); i++) {
      JSONObject col = columns.getJSONObject(i);
      String permCode = col.getString("permCode");
      if (permCode != null && !permCode.isEmpty()) {
        JSONObject data = new JSONObject();
        data.put("page_id", pageId);
        data.put("perm_type", "column");
        data.put("item_key", col.getString("dataField"));
        data.put("item_label", col.getString("caption"));
        data.put("perm_code", permCode);
        data.put("sort_order", sortOrder++);
        data.put("updated_at", now);
        SqlEngineJson.check(helper.insert(TABLE, data));
      }
    }
    return sortOrder;
  }

  /** 从 props.toolbar.items 提取按钮权限码（支持 JSONArray 和 JSON 字符串两种存储格式） */
  private int extractFromToolbarItems(JSONObject props, String pageId, String now, int sortOrder) throws Exception {
    JSONArray items = null;
    JSONObject toolbar = props.getJSONObject("toolbar");
    if (toolbar != null) {
      items = resolveArray(toolbar, "items");
    }
    if (items == null) {
      items = resolveArray(props, "toolbarItems");
    }
    if (items == null) {
      return sortOrder;
    }
    for (int i = 0; i < items.size(); i++) {
      JSONObject item = items.getJSONObject(i);
      String permCode = item.getString("permCode");
      if (permCode != null && !permCode.isEmpty()) {
        JSONObject data = new JSONObject();
        data.put("page_id", pageId);
        data.put("perm_type", "button");
        data.put("item_key", item.getString("name"));
        data.put("item_label", item.getString("caption"));
        data.put("perm_code", permCode);
        data.put("sort_order", sortOrder++);
        data.put("updated_at", now);
        SqlEngineJson.check(helper.insert(TABLE, data));
      }
    }
    return sortOrder;
  }

  /** 从 JSONObject 中按 key 取 JSONArray，兼容数组直存和 JSON 字符串两种格式 */
  private JSONArray resolveArray(JSONObject obj, String key) {
    Object val = obj.get(key);
    if (val == null) {
      return null;
    }
    if (val instanceof JSONArray) {
      return (JSONArray) val;
    }
    String str = String.valueOf(val);
    if (str.isEmpty()) {
      return null;
    }
    try {
      return JSONArray.parseArray(str);
    } catch (Exception e) {
      log.warn("[resolveArray] JSON 数组解析失败，权限码可能丢失: '{}'", str.substring(0, Math.min(100, str.length())));
      return null;
    }
  }

  /** 从组件节点自身的 permCode 属性提取（如 dx-button） */
  private int extractFromNodePerm(JSONObject props, String nodeType, String pageId, String now, int sortOrder)
      throws Exception {
    String permCode = props.getString("permCode");
    if (permCode == null || permCode.isEmpty()) {
      return sortOrder;
    }
    JSONObject data = new JSONObject();
    data.put("page_id", pageId);
    data.put("perm_type", "button");
    data.put("item_key", nodeType != null ? nodeType : "component");
    data.put("item_label", props.getString("text"));
    data.put("perm_code", permCode);
    data.put("sort_order", sortOrder++);
    data.put("updated_at", now);
    SqlEngineJson.check(helper.insert(TABLE, data));
    return sortOrder;
  }

  /** 查询某页面的所有权限码 */
  public JSONArray listByPage(String pageId) throws Exception {
    String[] cols = { "id", "page_id", "perm_type", "item_key", "item_label", "perm_code", "sort_order", "updated_at" };
    return helper.queryAndMap(TABLE, cols, helper.eq("page_id", pageId), "sort_order", false, MAPPING);
  }

  /** 按权限码查询（后续授权阶段使用） */
  public JSONArray listByPermCode(String permCode) throws Exception {
    String[] cols = { "id", "page_id", "perm_type", "item_key", "item_label", "perm_code", "sort_order", "updated_at" };
    return helper.queryAndMap(TABLE, cols, helper.eq("perm_code", permCode), "page_id", false, MAPPING);
  }

  /** 删除某页面的所有权限记录 */
  public void deleteByPage(String pageId) throws Exception {
    SqlEngineJson.check(helper.delete(TABLE, helper.eq("page_id", pageId)));
  }
}