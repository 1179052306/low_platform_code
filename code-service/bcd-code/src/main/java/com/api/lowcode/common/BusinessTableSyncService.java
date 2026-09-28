package com.api.lowcode.common;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.db.MultiDataSourceHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 业务表同步服务：保存页面时自动检测业务表是否存在，不存在则建表建字段。
 *
 * <p>
 * 从页面 schema 的 bizSchemas.form 中提取表单控件的 dbField，
 * 用 JdbcTemplate 执行 DDL（SqlEngineFacade 仅支持 DML/SELECT）。
 * </p>
 */
@Slf4j
@Service
public class BusinessTableSyncService {

  @Resource(name = "multiDataSource")
  private DataSource dataSource;

  /** 控件类型 → 数据库字段类型映射 */
  private static final java.util.Map<String, String> TYPE_MAP = new java.util.HashMap<>();

  static {
    TYPE_MAP.put("dx-text-box", "VARCHAR(255)");
    TYPE_MAP.put("dx-text-area", "TEXT");
    TYPE_MAP.put("dx-number-box", "NUMERIC(18,6)");
    TYPE_MAP.put("dx-date-box", "TIMESTAMP");
    TYPE_MAP.put("dx-date-time-box", "TIMESTAMP");
    TYPE_MAP.put("dx-check-box", "BOOLEAN");
    TYPE_MAP.put("dx-switch", "BOOLEAN");
    TYPE_MAP.put("dx-select-box", "VARCHAR(255)");
    TYPE_MAP.put("dx-color-box", "VARCHAR(32)");
    TYPE_MAP.put("dx-tag-box", "VARCHAR(255)");
    TYPE_MAP.put("dx-lookup", "VARCHAR(255)");
    TYPE_MAP.put("dx-auto-complete", "VARCHAR(255)");
    TYPE_MAP.put("dx-radio-group", "VARCHAR(255)");
    TYPE_MAP.put("dx-slider", "NUMERIC(18,6)");
    TYPE_MAP.put("dx-range-slider", "VARCHAR(255)");
    TYPE_MAP.put("dx-progress-bar", "NUMERIC(5,2)");
    TYPE_MAP.put("dx-recurrence-editor", "VARCHAR(255)");
  }

  private static final String DEFAULT_COLUMN_TYPE = "VARCHAR(255)";

  /** 系统字段（非业务字段，不参与孤儿字段管理） */
  private static final Set<String> SYSTEM_COLUMNS = new HashSet<>(java.util.Arrays.asList(
      "id", "created_at", "updated_at", "created_by", "updated_by", "deleted"
  ));

  /** 标识符校验正则：字母开头，只含字母/数字/下划线，1~64字符 */
  private static final java.util.regex.Pattern IDENTIFIER_RE =
      java.util.regex.Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");

  /** 校验标识符合法性 */
  private boolean isValidIdentifier(String name) {
    return name != null && IDENTIFIER_RE.matcher(name).matches();
  }

  /**
   * 根据页面 schema 同步业务表结构。
   *
   * @param schema 页面 schema JSON
   * @return 同步结果消息（null 表示未执行建表，如非 document/basedata 类型）
   */
  public String syncTable(JSONObject schema) {
    String pageType = schema.getString("pageType");
    // 仅单据/基础资料类型需要建表
    if (!"document".equals(pageType) && !"basedata".equals(pageType)) {
      return null;
    }

    String tableName = schema.getString("tableName");
    if (tableName == null || tableName.trim().isEmpty()) {
      return null;
    }
    tableName = tableName.trim();

    // 表名特殊字符校验
    if (!isValidIdentifier(tableName)) {
      log.warn("[TableSync] 表名 '{}' 不合法（需字母开头+字母/数字/下划线），跳过建表", tableName);
      return "表名不合法，跳过建表";
    }

    // 从 bizSchemas.form 提取表单控件的 dbField
    List<FieldDef> fields = extractFormFields(schema);
    if (fields.isEmpty()) {
      log.info("[TableSync] 页面 {} 无表单字段绑定，跳过建表", tableName);
      return "无表单字段绑定，跳过建表";
    }

    MultiDataSourceHolder.setDatasource("lowcode");
    try {
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);

      if (!tableExists(jdbc, tableName)) {
        createTable(jdbc, tableName, fields);
        return "已创建表 " + tableName + "，字段数 " + fields.size();
      } else {
        int added = addMissingColumns(jdbc, tableName, fields);
        return added > 0 ? "已添加 " + added + " 个字段到表 " + tableName : "表 " + tableName + " 已是最新";
      }
    } catch (Exception e) {
      log.error("[TableSync] 同步业务表 {} 失败: {}", tableName, e.getMessage(), e);
      return "同步失败: " + e.getMessage();
    } finally {
      MultiDataSourceHolder.clearDataSource();
    }
  }

  /** 从 schema 的 bizSchemas.form 递归提取有 dbField 的表单控件 */
  private List<FieldDef> extractFormFields(JSONObject schema) {
    List<FieldDef> fields = new ArrayList<>();
    JSONObject bizSchemas = schema.getJSONObject("bizSchemas");
    if (bizSchemas == null) {
      return fields;
    }
    JSONArray formRoot = bizSchemas.getJSONArray("form");
    if (formRoot == null) {
      return fields;
    }
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < formRoot.size(); i++) {
      collectFields(formRoot.getJSONObject(i), fields, seen);
    }
    return fields;
  }

  /** 递归收集节点树中有 dbField 的控件 */
  private void collectFields(JSONObject node, List<FieldDef> fields, Set<String> seen) {
    if (node == null) return;
    String dbField = node.getString("dbField");
    String type = node.getString("type");
    if (dbField != null && !dbField.trim().isEmpty() && !seen.contains(dbField) && isValidIdentifier(dbField.trim())) {
      seen.add(dbField);
      String columnType = TYPE_MAP.getOrDefault(type, DEFAULT_COLUMN_TYPE);
      fields.add(new FieldDef(dbField.trim(), columnType));
    } else if (dbField != null && !dbField.trim().isEmpty() && !isValidIdentifier(dbField.trim())) {
      log.warn("[TableSync] 字段名 '{}' 不合法，跳过", dbField);
    }
    JSONArray children = node.getJSONArray("children");
    if (children != null) {
      for (int i = 0; i < children.size(); i++) {
        collectFields(children.getJSONObject(i), fields, seen);
      }
    }
  }

  /** 检测表是否存在（PostgreSQL information_schema，表名带引号创建保留原大小写） */
  private boolean tableExists(JdbcTemplate jdbc, String tableName) {
    String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?";
    Integer count = jdbc.queryForObject(sql, Integer.class, tableName);
    return count != null && count > 0;
  }

  /** 建表：CREATE TABLE + 所有字段 */
  private void createTable(JdbcTemplate jdbc, String tableName, List<FieldDef> fields) {
    StringBuilder sb = new StringBuilder();
    sb.append("CREATE TABLE \"").append(tableName).append("\" (");
    sb.append("\n  \"id\" VARCHAR(64) PRIMARY KEY");
    sb.append(",\n  \"created_at\" TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
    sb.append(",\n  \"updated_at\" TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
    sb.append(",\n  \"created_by\" VARCHAR(64)");
    sb.append(",\n  \"updated_by\" VARCHAR(64)");
    sb.append(",\n  \"deleted\" BOOLEAN DEFAULT FALSE");
    for (FieldDef f : fields) {
      sb.append(",\n  \"").append(f.name).append("\" ").append(f.type);
    }
    sb.append("\n)");
    log.info("[TableSync] 建表: {}", tableName);
    jdbc.execute(sb.toString());
  }

  /** 检测并添加缺失的字段，返回新增字段数 */
  private int addMissingColumns(JdbcTemplate jdbc, String tableName, List<FieldDef> fields) {
    Set<String> existingCols = getExistingColumns(jdbc, tableName);
    int added = 0;
    for (FieldDef f : fields) {
      if (!existingCols.contains(f.name.toLowerCase())) {
        String sql = "ALTER TABLE \"" + tableName + "\" ADD COLUMN \"" + f.name + "\" " + f.type;
        log.info("[TableSync] 加字段: {}.{} {}", tableName, f.name, f.type);
        jdbc.execute(sql);
        added++;
      }
    }
    return added;
  }

  /** 获取表已有的字段列表（表名带引号创建保留原大小写） */
  private Set<String> getExistingColumns(JdbcTemplate jdbc, String tableName) {
    String sql = "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?";
    List<String> cols = jdbc.queryForList(sql, String.class, tableName);
    Set<String> result = new HashSet<>();
    for (String col : cols) {
      result.add(col.toLowerCase());
    }
    return result;
  }

  /**
   * 查询孤儿字段：数据库有但 schema 中没有的业务字段，标注是否有数据。
   *
   * @param tableName 表名
   * @param schemaFieldNames schema 中已有的 dbField 列表
   * @return JSONArray，每个元素 {name, hasData}
   */
  public JSONArray getOrphanFields(String tableName, List<String> schemaFieldNames) {
    JSONArray result = new JSONArray();
    if (tableName == null || tableName.trim().isEmpty() || !isValidIdentifier(tableName.trim())) {
      return result;
    }
    tableName = tableName.trim();

    MultiDataSourceHolder.setDatasource("lowcode");
    try {
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      if (!tableExists(jdbc, tableName)) {
        return result;
      }

      String colSql = "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?";
      List<String> dbCols = jdbc.queryForList(colSql, String.class, tableName);

      Set<String> schemaCols = new HashSet<>();
      if (schemaFieldNames != null) {
        for (String f : schemaFieldNames) {
          if (f != null) schemaCols.add(f.toLowerCase());
        }
      }

      for (String col : dbCols) {
        if (SYSTEM_COLUMNS.contains(col.toLowerCase())) continue;
        if (schemaCols.contains(col.toLowerCase())) continue;

        JSONObject item = new JSONObject();
        item.put("name", col);
        String checkSql = "SELECT COUNT(*) FROM \"" + tableName + "\" WHERE \"" + col + "\" IS NOT NULL";
        Long count = jdbc.queryForObject(checkSql, Long.class);
        item.put("hasData", count != null && count > 0);
        result.add(item);
      }
    } catch (Exception e) {
      log.error("[TableSync] 查询孤儿字段失败: {}", e.getMessage(), e);
    } finally {
      MultiDataSourceHolder.clearDataSource();
    }
    return result;
  }

  /**
   * 删除字段：仅当字段全为 NULL 时才允许删除。
   *
   * @param tableName 表名
   * @param columnName 字段名（原始大小写）
   */
  public void dropColumn(String tableName, String columnName) {
    if (!isValidIdentifier(tableName) || !isValidIdentifier(columnName)) {
      throw new IllegalArgumentException("表名或字段名不合法");
    }

    MultiDataSourceHolder.setDatasource("lowcode");
    try {
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);

      if (!tableExists(jdbc, tableName)) {
        throw new IllegalArgumentException("表 " + tableName + " 不存在");
      }

      String checkSql = "SELECT COUNT(*) FROM \"" + tableName + "\" WHERE \"" + columnName + "\" IS NOT NULL";
      Long count = jdbc.queryForObject(checkSql, Long.class);
      if (count != null && count > 0) {
        throw new IllegalStateException("字段 " + columnName + " 有 " + count + " 条非空数据，不允许删除");
      }

      String sql = "ALTER TABLE \"" + tableName + "\" DROP COLUMN \"" + columnName + "\"";
      log.info("[TableSync] 删字段: {}.{}", tableName, columnName);
      jdbc.execute(sql);
    } finally {
      MultiDataSourceHolder.clearDataSource();
    }
  }

  /** 字段定义 */
  private static class FieldDef {
    final String name;
    final String type;

    FieldDef(String name, String type) {
      this.name = name;
      this.type = type;
    }
  }
}