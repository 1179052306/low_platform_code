package com.api.lowcode.theme;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ThemeService {

  @Resource
  private SqlEngineJson helper;

  private static final String THEME_TABLE = "lowcode_theme";
  private static final String CONFIG_TABLE = "lowcode_theme_config";
  private static final String PAGE_TABLE = "lowcode_page";

  private static final String[][] THEME_MAPPING = {
      { "id", "id" }, { "name", "name" }, { "is_preset", "isPreset" },
      { "theme_json", "themeJson" }, { "create_time", "createTime" }, { "update_time", "updateTime" }
  };

  private static final String[] COLOR_KEYS = {
      "primary", "primaryHover", "success", "warning", "danger", "info",
      "pageBg", "cardBg", "textPrimary", "textSecondary", "border"
  };

  private static final Pattern HEX_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

  /** 列出所有主题（预置+自定义），keyword 内存过滤名称 */
  public JSONArray listThemes(String keyword) throws Exception {
    String[] cols = { "id", "name", "is_preset", "theme_json", "create_time", "update_time" };
    JSONArray rows = helper.queryAndMap(THEME_TABLE, cols, null, "id", false, THEME_MAPPING);
    JSONArray result = new JSONArray();
    String kw = keyword == null ? "" : keyword.trim().toLowerCase();
    for (int i = 0; i < rows.size(); i++) {
      JSONObject item = rows.getJSONObject(i);
      String name = item.getString("name");
      if (!kw.isEmpty() && (name == null || !name.toLowerCase().contains(kw))) {
        continue;
      }
      String jsonStr = item.getString("themeJson");
      item.remove("themeJson");
      if (jsonStr != null) {
        try {
          JSONObject themeJson = JSONObject.parseObject(jsonStr);
          item.put("colors", themeJson.getJSONObject("colors"));
          item.put("radius", themeJson.getIntValue("radius", 0));
        } catch (Exception e) {
          log.warn("[listThemes] 主题 {} 的 themeJson 解析失败: {}", item.getString("id"), e.getMessage());
          throw new RuntimeException("主题 '" + name + "' 的配置数据损坏，无法解析");
        }
      }
      result.add(item);
    }
    return result;
  }

  /** 保存主题（新增/编辑自定义主题，id 由前端生成，预置不可编辑） */
  public String saveTheme(JSONObject req) throws Exception {
    String id = req.getString("id");
    if (id == null || id.isEmpty()) {
      throw new IllegalArgumentException("id 不能为空");
    }
    JSONArray exist = helper.query(THEME_TABLE, new String[] { "is_preset" }, helper.eq("id", id), null, false);
    if (!exist.isEmpty() && exist.getJSONObject(0).getBooleanValue("IS_PRESET")) {
      throw new IllegalArgumentException("预置主题不可编辑");
    }
    String name = req.getString("name");
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("主题名称不能为空");
    }
    JSONObject colors = req.getJSONObject("colors");
    if (colors == null) {
      throw new IllegalArgumentException("主题颜色不能为空");
    }
    for (String k : COLOR_KEYS) {
      String v = colors.getString(k);
      if (v == null || !HEX_PATTERN.matcher(v).matches()) {
        throw new IllegalArgumentException("颜色 " + k + " 格式不合法，需为 #rrggbb");
      }
    }
    int radius = req.getIntValue("radius", 0);
    JSONObject themeJson = new JSONObject();
    themeJson.put("colors", colors);
    themeJson.put("radius", radius);
    String jsonStr = themeJson.toJSONString();
    if (jsonStr.length() > 65536) {
      throw new IllegalArgumentException("主题配置过大");
    }
    JSONArray all = helper.queryAndMap(THEME_TABLE, new String[] { "id", "name" }, null, null, false,
        new String[][] { { "id", "id" }, { "name", "name" } });
    for (int i = 0; i < all.size(); i++) {
      JSONObject t = all.getJSONObject(i);
      if (name.equals(t.getString("name")) && !id.equals(t.getString("id"))) {
        throw new IllegalArgumentException("主题名称已存在");
      }
    }
    String now = Instant.now().toString();
    JSONObject data = new JSONObject();
    data.put("id", id);
    data.put("name", name);
    data.put("is_preset", false);
    data.put("theme_json", jsonStr);
    data.put("create_time", now);
    data.put("update_time", now);
    SqlEngineJson.check(helper.upsert(THEME_TABLE, data, helper.eq("id", id)));
    return id;
  }

  /** 删除主题（预置不可删） */
  public void deleteTheme(String id) throws Exception {
    JSONArray exist = helper.query(THEME_TABLE, new String[] { "is_preset" }, helper.eq("id", id), null, false);
    if (exist.isEmpty()) {
      throw new IllegalArgumentException("主题不存在");
    }
    if (exist.getJSONObject(0).getBooleanValue("IS_PRESET")) {
      throw new IllegalArgumentException("预置主题不可删除");
    }
    SqlEngineJson.check(helper.delete(THEME_TABLE, helper.eq("id", id)));
  }

  /** 查询单个主题 */
  public JSONObject getTheme(String id) throws Exception {
    String[] cols = { "id", "name", "is_preset", "theme_json" };
    JSONArray rows = helper.queryAndMap(THEME_TABLE, cols, helper.eq("id", id), null, false, THEME_MAPPING);
    if (rows.isEmpty()) {
      return null;
    }
    JSONObject item = rows.getJSONObject(0);
    String jsonStr = item.getString("themeJson");
    item.remove("themeJson");
    if (jsonStr != null) {
      try {
        JSONObject themeJson = JSONObject.parseObject(jsonStr);
        item.put("colors", themeJson.getJSONObject("colors"));
        item.put("radius", themeJson.getIntValue("radius", 0));
      } catch (Exception e) {
        log.warn("[getTheme] 主题 {} 的 themeJson 解析失败: {}", id, e.getMessage());
        throw new RuntimeException("主题 '" + item.getString("name") + "' 的配置数据损坏，无法解析");
      }
    }
    return item;
  }

  /** 获取默认主题 id */
  public String getDefaultThemeId() throws Exception {
    String[] cols = { "config_value" };
    JSONArray rows = helper.query(CONFIG_TABLE, cols, helper.eq("config_key", "default_theme_id"), null, false);
    if (rows.isEmpty()) {
      return null;
    }
    return rows.getJSONObject(0).getString("CONFIG_VALUE");
  }

  /** 设置默认主题 */
  public void setDefaultThemeId(String themeId) throws Exception {
    JSONObject data = new JSONObject();
    data.put("config_key", "default_theme_id");
    data.put("config_value", themeId);
    SqlEngineJson.check(helper.upsert(CONFIG_TABLE, data, helper.eq("config_key", "default_theme_id")));
  }

  /** 批量应用主题到多个页面（更新 schema_json 中的 themeId） */
  public void batchApply(String themeId, List<String> pageIds) throws Exception {
    String now = Instant.now().toString();
    for (String pageId : pageIds) {
      String[] cols = { "schema_json" };
      JSONArray rows = helper.query(PAGE_TABLE, cols, helper.eq("page_id", pageId), null, false);
      if (rows.isEmpty()) {
        continue;
      }
      String json = rows.getJSONObject(0).getString("SCHEMA_JSON");
      if (json == null) {
        continue;
      }
      JSONObject schema = JSONObject.parseObject(json);
      schema.put("themeId", themeId);
      JSONObject data = new JSONObject();
      data.put("schema_json", schema.toJSONString());
      data.put("saved_at", now);
      SqlEngineJson.check(helper.update(PAGE_TABLE, data, helper.eq("page_id", pageId)));
    }
  }
}