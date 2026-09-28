package com.api.lowcode.lang;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LangService {

  @Resource
  private SqlEngineJson helper;

  private static final String LANG_TABLE = "lowcode_lang";
  private static final String TEXT_TABLE = "lowcode_lang_text";

  private static final String[][] LANG_MAPPING = {
      { "locale", "locale" }, { "name", "name" }, { "sort_order", "sortOrder" },
      { "enabled", "enabled" }, { "is_rtl", "isRtl" }
  };
  private static final String[][] TEXT_MAPPING = {
      { "id", "id" }, { "locale", "locale" }, { "text_key", "textKey" },
      { "text_val", "textVal" }, { "module", "module" }, { "updated_at", "updatedAt" }
  };

  /** 列出所有启用的语言，按 sort_order 排序 */
  public JSONArray listLangs() throws Exception {
    String[] cols = { "locale", "name", "sort_order", "enabled", "is_rtl" };
    return helper.queryAndMap(LANG_TABLE, cols, helper.eq("enabled", true), "sort_order", false, LANG_MAPPING);
  }

  /** 列出所有语言（含禁用），按 sort_order 排序 —— 翻译管理页面用 */
  public JSONArray listAllLangs() throws Exception {
    String[] cols = { "locale", "name", "sort_order", "enabled", "is_rtl" };
    return helper.queryAndMap(LANG_TABLE, cols, null, "sort_order", false, LANG_MAPPING);
  }

  /** 新增语言 */
  public void addLang(JSONObject lang) throws Exception {
    String locale = lang.getString("locale");
    if (locale == null || locale.isEmpty()) {
      throw new IllegalArgumentException("locale 不能为空");
    }
    String name = lang.getString("name");
    int sortOrder = lang.getIntValue("sortOrder", 0);
    boolean enabled = lang.getBooleanValue("enabled", true);
    boolean isRtl = lang.getBooleanValue("isRtl", false);
    String now = Instant.now().toString();

    JSONObject data = new JSONObject();
    data.put("locale", locale);
    data.put("name", name);
    data.put("sort_order", sortOrder);
    data.put("enabled", enabled);
    data.put("is_rtl", isRtl);
    data.put("created_at", now);

    JSONArray conds = new JSONArray();
    conds.add(helper.eq("locale", locale).getJSONObject(1));
    SqlEngineJson.check(helper.upsert(LANG_TABLE, data, conds));
  }

  /** 编辑语言（按 locale 主键更新） */
  public void updateLang(JSONObject lang) throws Exception {
    String locale = lang.getString("locale");
    if (locale == null || locale.isEmpty()) {
      throw new IllegalArgumentException("locale 不能为空");
    }
    JSONObject data = new JSONObject();
    if (lang.containsKey("name")) {
      data.put("name", lang.getString("name"));
    }
    if (lang.containsKey("sortOrder")) {
      data.put("sort_order", lang.getIntValue("sortOrder"));
    }
    if (lang.containsKey("enabled")) {
      data.put("enabled", lang.getBooleanValue("enabled"));
    }
    if (lang.containsKey("isRtl")) {
      data.put("is_rtl", lang.getBooleanValue("isRtl"));
    }
    if (data.isEmpty()) {
      return;
    }
    SqlEngineJson.check(helper.update(LANG_TABLE, data, helper.eq("locale", locale)));
  }

  /** 删除语言（同时删除该语言的所有翻译文本） */
  public void deleteLang(String locale) throws Exception {
    if ("zh-CN".equals(locale)) {
      throw new IllegalArgumentException("简体中文为默认语言，不可删除");
    }
    SqlEngineJson.check(helper.delete(TEXT_TABLE, helper.eq("locale", locale)));
    SqlEngineJson.check(helper.delete(LANG_TABLE, helper.eq("locale", locale)));
  }

  /**
   * 加载翻译字典。
   *
   * @param locale  目标语言
   * @param modules 模块过滤，null/empty 表示加载全部
   * @return JSONObject，key=中文原文，value=译文
   */
  public JSONObject loadText(String locale, List<String> modules) throws Exception {
    if ("zh-CN".equals(locale)) {
      return new JSONObject();
    }

    String[] cols = { "text_key", "text_val" };
    JSONArray conditions = buildLocaleConditions(locale, modules);
    JSONArray rows = helper.query(TEXT_TABLE, cols, conditions, null, false);

    JSONObject dict = new JSONObject();
    for (int i = 0; i < rows.size(); i++) {
      JSONObject row = rows.getJSONObject(i);
      String key = row.getString("TEXT_KEY");
      String val = row.getString("TEXT_VAL");
      if (key != null && val != null) {
        dict.put(key, val);
      }
    }
    return dict;
  }

  /**
   * 分页查询翻译文本（翻译管理页面用）。
   *
   * @param locale  目标语言
   * @param keyword 模糊搜索（匹配 text_key 或 text_val），null/empty 表示不搜索
   * @param page    页码（1-based）
   * @param size    每页条数
   * @return JSONObject {list, total}
   */
  public JSONObject pageText(String locale, String keyword, int page, int size) throws Exception {
    String[] cols = { "id", "locale", "text_key", "text_val", "module", "updated_at" };
    JSONArray rows = helper.queryAndMap(TEXT_TABLE, cols, helper.eq("locale", locale), "id", false, TEXT_MAPPING);

    List<JSONObject> filtered = new ArrayList<JSONObject>();
    String kw = keyword == null ? "" : keyword.trim().toLowerCase();
    for (int i = 0; i < rows.size(); i++) {
      JSONObject item = rows.getJSONObject(i);
      if (kw.isEmpty()) {
        filtered.add(item);
        continue;
      }
      String key = item.getString("textKey");
      String val = item.getString("textVal");
      boolean match = (key != null && key.toLowerCase().contains(kw))
          || (val != null && val.toLowerCase().contains(kw));
      if (match) {
        filtered.add(item);
      }
    }

    int total = filtered.size();
    int from = Math.min((page - 1) * size, total);
    int to = Math.min(from + size, total);

    JSONArray pageList = new JSONArray();
    for (int i = from; i < to; i++) {
      pageList.add(filtered.get(i));
    }

    JSONObject result = new JSONObject();
    result.put("list", pageList);
    result.put("total", total);
    return result;
  }

  /** 保存单条翻译（upsert） */
  public void saveText(JSONObject item) throws Exception {
    String locale = item.getString("locale");
    String textKey = item.getString("textKey");
    String textVal = item.getString("textVal");
    String module = item.getString("module");
    if (module == null || module.isEmpty()) {
      module = "common";
    }
    String now = Instant.now().toString();

    JSONObject data = new JSONObject();
    data.put("locale", locale);
    data.put("text_key", textKey);
    data.put("text_val", textVal);
    data.put("module", module);
    data.put("updated_at", now);

    JSONArray conds = new JSONArray();
    conds.add(helper.eq("locale", locale).getJSONObject(1));
    conds.add(helper.eq("text_key", textKey).getJSONObject(1));
    SqlEngineJson.check(helper.upsert(TEXT_TABLE, data, conds));
  }

  /** 批量导入翻译 */
  public void batchImport(String locale, JSONArray items) throws Exception {
    for (int i = 0; i < items.size(); i++) {
      JSONObject item = items.getJSONObject(i);
      item.put("locale", locale);
      saveText(item);
    }
  }

  /** 删除单条翻译 */
  public void deleteText(int id) throws Exception {
    SqlEngineJson.check(helper.delete(TEXT_TABLE, helper.eq("id", id)));
  }

  // ==================== 自动翻译 ====================

  private static final Map<String, String> LOCALE_TO_LANG_CODE = new HashMap<String, String>();
  static {
    LOCALE_TO_LANG_CODE.put("zh-CN", "zh");
    LOCALE_TO_LANG_CODE.put("en-US", "en");
    LOCALE_TO_LANG_CODE.put("ja-JP", "ja");
    LOCALE_TO_LANG_CODE.put("ko-KR", "ko");
    LOCALE_TO_LANG_CODE.put("ar-SA", "ar");
    LOCALE_TO_LANG_CODE.put("fr-FR", "fr");
    LOCALE_TO_LANG_CODE.put("de-DE", "de");
    LOCALE_TO_LANG_CODE.put("es-ES", "es");
    LOCALE_TO_LANG_CODE.put("ru-RU", "ru");
    LOCALE_TO_LANG_CODE.put("pt-PT", "pt");
    LOCALE_TO_LANG_CODE.put("it-IT", "it");
    LOCALE_TO_LANG_CODE.put("tr-TR", "tr");
    LOCALE_TO_LANG_CODE.put("vi-VN", "vi");
    LOCALE_TO_LANG_CODE.put("th-TH", "th");
    LOCALE_TO_LANG_CODE.put("id-ID", "id");
    LOCALE_TO_LANG_CODE.put("ms-MY", "ms");
    LOCALE_TO_LANG_CODE.put("nl-NL", "nl");
    LOCALE_TO_LANG_CODE.put("pl-PL", "pl");
    LOCALE_TO_LANG_CODE.put("uk-UA", "uk");
    LOCALE_TO_LANG_CODE.put("hi-IN", "hi");
  }

  /**
   * 自动翻译：调用 MyMemory 免费 API 将中文文本翻译为目标语言。
   *
   * @param texts      中文原文列表
   * @param targetLang 目标语言 locale（如 en-US）
   * @return JSONObject，key=中文原文，value=译文
   */
  public JSONObject autoTranslate(List<String> texts, String targetLang) throws Exception {
    String targetCode = LOCALE_TO_LANG_CODE.get(targetLang);
    if (targetCode == null) {
      targetCode = targetLang.split("-")[0].toLowerCase();
    }
    String langPair = "zh|" + targetCode;

    JSONObject result = new JSONObject();
    int failCount = 0;
    for (String text : texts) {
      if (text == null || text.trim().isEmpty()) {
        continue;
      }
      try {
        String translated = callMyMemory(text, langPair);
        if (translated != null && !translated.isEmpty()) {
          result.put(text, translated);
        }
      } catch (Exception e) {
        failCount++;
        log.warn("[autoTranslate] 翻译失败: '{}' -> {}", text, e.getMessage());
      }
    }
    // 全部失败时抛异常，让 Controller 返回错误给前端
    if (failCount > 0 && result.isEmpty()) {
      throw new RuntimeException("自动翻译全部失败（共 " + failCount + " 条），请检查网络或翻译服务");
    }
    return result;
  }

  /** 调用 MyMemory Translation API */
  private String callMyMemory(String text, String langPair) throws Exception {
    String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.name());
    String urlStr = "https://api.mymemory.translated.net/get?q=" + encoded + "&langpair=" + langPair;

    URL url = new URL(urlStr);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setConnectTimeout(5000);
    conn.setReadTimeout(10000);
    conn.setRequestProperty("User-Agent", "Mozilla/5.0");

    int code = conn.getResponseCode();
    if (code != 200) {
      return null;
    }

    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line);
      }
    }
    conn.disconnect();

    JSONObject resp = JSONObject.parseObject(sb.toString());
    JSONObject responseData = resp.getJSONObject("responseData");
    if (responseData != null) {
      return responseData.getString("translatedText");
    }
    return null;
  }

  // ==================== 导出/导入 ====================

  /**
   * 导出翻译数据。
   *
   * @param locale  目标语言，null 表示导出所有语言
   * @param modules 模块过滤，null/empty 表示导出所有模块
   * @return JSONArray，每条 {locale, textKey, textVal, module}
   */
  public JSONArray exportLang(String locale, List<String> modules) throws Exception {
    String[] cols = { "locale", "text_key", "text_val", "module" };
    JSONArray conditions = null;

    boolean hasLocale = locale != null && !locale.isEmpty();
    boolean hasModules = modules != null && !modules.isEmpty();

    if (hasLocale && hasModules) {
      conditions = new JSONArray();
      JSONObject andConn = new JSONObject();
      andConn.put("Symbol", "and");
      conditions.add(andConn);
      JSONObject localeCond = new JSONObject();
      localeCond.put("Id", "locale");
      localeCond.put("Symbol", "=");
      localeCond.put("Val", locale);
      localeCond.put("TableAlias", "T");
      conditions.add(localeCond);
      JSONObject andConn2 = new JSONObject();
      andConn2.put("Symbol", "and");
      conditions.add(andConn2);
      JSONObject moduleCond = new JSONObject();
      moduleCond.put("Id", "module");
      moduleCond.put("Symbol", "in");
      JSONArray modArr = new JSONArray();
      for (String m : modules) {
        modArr.add(m);
      }
      moduleCond.put("Val", modArr);
      moduleCond.put("TableAlias", "T");
      conditions.add(moduleCond);
    } else if (hasLocale) {
      conditions = helper.eq("locale", locale);
    } else if (hasModules) {
      conditions = new JSONArray();
      JSONObject andConn = new JSONObject();
      andConn.put("Symbol", "and");
      conditions.add(andConn);
      JSONObject moduleCond = new JSONObject();
      moduleCond.put("Id", "module");
      moduleCond.put("Symbol", "in");
      JSONArray modArr = new JSONArray();
      for (String m : modules) {
        modArr.add(m);
      }
      moduleCond.put("Val", modArr);
      moduleCond.put("TableAlias", "T");
      conditions.add(moduleCond);
    }

    JSONArray rows = helper.query(TEXT_TABLE, cols, conditions, null, false);

    JSONArray result = new JSONArray();
    for (int i = 0; i < rows.size(); i++) {
      JSONObject row = rows.getJSONObject(i);
      JSONObject item = new JSONObject();
      item.put("locale", row.getString("LOCALE"));
      item.put("textKey", row.getString("TEXT_KEY"));
      item.put("textVal", row.getString("TEXT_VAL"));
      item.put("module", row.getString("MODULE"));
      result.add(item);
    }
    return result;
  }

  /**
   * 批量导入翻译数据（跨语言，每条自带 locale）。
   *
   * @param data JSONArray，每条 {locale, textKey, textVal, module}
   * @return 导入条数
   */
  public int importBatch(JSONArray data) throws Exception {
    int count = 0;
    for (int i = 0; i < data.size(); i++) {
      JSONObject item = data.getJSONObject(i);
      String loc = item.getString("locale");
      if (loc == null || loc.isEmpty()) {
        continue;
      }
      saveText(item);
      count++;
    }
    return count;
  }

  /** 构建 locale 条件，可选追加 module IN (...) */
  private JSONArray buildLocaleConditions(String locale, List<String> modules) {
    JSONArray conditions = new JSONArray();
    JSONObject connector = new JSONObject();
    connector.put("Symbol", "and");
    conditions.add(connector);

    JSONObject localeCond = new JSONObject();
    localeCond.put("Id", "locale");
    localeCond.put("Symbol", "=");
    localeCond.put("Val", locale);
    localeCond.put("TableAlias", "T");
    conditions.add(localeCond);

    if (modules != null && !modules.isEmpty()) {
      JSONObject andConn = new JSONObject();
      andConn.put("Symbol", "and");
      conditions.add(andConn);

      JSONObject moduleCond = new JSONObject();
      moduleCond.put("Id", "module");
      moduleCond.put("Symbol", "in");
      JSONArray modArr = new JSONArray();
      for (String m : modules) {
        modArr.add(m);
      }
      moduleCond.put("Val", modArr);
      moduleCond.put("TableAlias", "T");
      conditions.add(moduleCond);
    }

    return conditions;
  }
}