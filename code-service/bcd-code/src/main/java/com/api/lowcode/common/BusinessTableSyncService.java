package com.api.lowcode.common;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.db.MultiDataSourceHolder;
import com.server.sqlengine.dialect.SqlDialect;
import com.server.sqlengine.dialect.SqlDialectRegistry;
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
 * 标识符引用通过 {@link SqlDialect#quoteIdentifier} 处理，元数据查询通过方言提供的 SQL。
 * </p>
 */
@Slf4j
@Service
public class BusinessTableSyncService {

  @Resource(name = "multiDataSource")
  private DataSource dataSource;

  @Resource
  private SqlDialectRegistry dialectRegistry;

  @Resource
  private com.server.sqlengine.validator.SqlFieldValidator sqlFieldValidator;

  private static final String DB_NAME = "lowcode";

  /** 控件类型 → 数据库字段类型映射 */
  private static final java.util.Map<String, String> TYPE_MAP = new java.util.HashMap<>();

  static {
    TYPE_MAP.put("text-box", "VARCHAR(255)");
    TYPE_MAP.put("text-area", "TEXT");
    TYPE_MAP.put("number-box", "NUMERIC(18,6)");
    TYPE_MAP.put("date-box", "TIMESTAMP");
    TYPE_MAP.put("check-box", "BOOLEAN");
    TYPE_MAP.put("switch", "BOOLEAN");
    TYPE_MAP.put("select-box", "VARCHAR(255)");
    TYPE_MAP.put("color-box", "VARCHAR(32)");
    TYPE_MAP.put("tag-box", "VARCHAR(255)");
    TYPE_MAP.put("lookup", "VARCHAR(255)");
    TYPE_MAP.put("radio-group", "VARCHAR(255)");
    TYPE_MAP.put("slider", "NUMERIC(18,6)");
    TYPE_MAP.put("range-slider", "VARCHAR(255)");
    TYPE_MAP.put("progress-bar", "NUMERIC(5,2)");
  }

  private static final String DEFAULT_COLUMN_TYPE = "VARCHAR(255)";

  /** 表格列 dataType → 数据库字段类型映射（明细表用） */
  private static final java.util.Map<String, String> COLUMN_TYPE_MAP = new java.util.HashMap<>();
  static {
    COLUMN_TYPE_MAP.put("string", "VARCHAR(255)");
    COLUMN_TYPE_MAP.put("number", "NUMERIC(18,6)");
    COLUMN_TYPE_MAP.put("date", "DATE");
    COLUMN_TYPE_MAP.put("datetime", "TIMESTAMP");
    COLUMN_TYPE_MAP.put("boolean", "BOOLEAN");
  }

  /** 系统字段（非业务字段，不参与孤儿字段管理） */
  private static final Set<String> SYSTEM_COLUMNS = new HashSet<>(java.util.Arrays.asList(
      "id", "created_at", "updated_at", "created_by", "updated_by", "deleted"
  ));

  /** 标识符校验正则：字母开头，只含字母/数字/下划线，1~64字符 */
  private static final java.util.regex.Pattern IDENTIFIER_RE =
      java.util.regex.Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");

  /** 获取当前数据源的方言 */
  private SqlDialect dialect() {
    return dialectRegistry.dialect(DB_NAME);
  }

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

    MultiDataSourceHolder.setDatasource(DB_NAME);
    try {
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      SqlDialect dialect = dialect();

      // 基础资料关联字段：列类型跟随被关联页面的主键类型
      java.util.Map<String, String> baseDataColTypes = resolveBaseDataColumnTypes(schema, jdbc);
      for (FieldDef f : fields) {
        String bdType = baseDataColTypes.get(f.name.toUpperCase());
        if (bdType != null) f.type = bdType;
      }

      String pkField = schema.getString("primaryKeyField");
      pkField = pkField != null && !pkField.trim().isEmpty() ? pkField.trim() : "id";

      String keyStrategy = schema.getString("keyStrategy");
      boolean useSnowflake = "SNOWFLAKE".equalsIgnoreCase(keyStrategy);

      if (!tableExists(jdbc, tableName, dialect)) {
        String pageName = schema.getString("pageName");
        createTable(jdbc, tableName, fields, dialect, pkField, useSnowflake, pageName);
        sqlFieldValidator.evictCache(DB_NAME, tableName);
        return "已创建表 " + tableName + "，字段数 " + fields.size();
      } else {
        int added = addMissingColumns(jdbc, tableName, fields, dialect);
        // 确保系统字段 status 存在（启用/禁用功能用）
        if (ensureStatusColumn(jdbc, tableName, dialect)) added++;
        if (added > 0) sqlFieldValidator.evictCache(DB_NAME, tableName);
        StringBuilder msg = new StringBuilder();
        if (added > 0) msg.append("已添加 ").append(added).append(" 个字段到表 ").append(tableName);
        else msg.append("表 ").append(tableName).append(" 已是最新");
        if (!"id".equalsIgnoreCase(pkField)) {
          msg.append("；注意：表已存在，主键为建表时的配置，如需改主键为 ").append(pkField)
             .append(" 请先删除表 ").append(tableName).append(" 后重新保存");
        }
        return msg.toString();
      }
    } catch (Exception e) {
      log.error("[TableSync] 同步业务表 {} 失败: {}", tableName, e.getMessage(), e);
      return "同步失败: " + e.getMessage();
    } finally {
      MultiDataSourceHolder.clearDataSource();
    }
  }

  /**
    * 遍历表单节点树，找到基础资料控件（base-data），
   * 查询被关联页面的主键类型，构建 dbField → 列类型 映射。
   * Snowflake → BIGINT，其余 → VARCHAR(64)。
   */
  private java.util.Map<String, String> resolveBaseDataColumnTypes(JSONObject schema, JdbcTemplate jdbc) {
    java.util.Map<String, String> map = new java.util.HashMap<>();
    JSONObject bizSchemas = schema.getJSONObject("bizSchemas");
    if (bizSchemas == null) return map;
    JSONArray formRoot = bizSchemas.getJSONArray("form");
    if (formRoot == null) return map;
    for (int i = 0; i < formRoot.size(); i++) {
      scanBaseDataNodes(formRoot.getJSONObject(i), map, jdbc);
    }
    return map;
  }

  private void scanBaseDataNodes(JSONObject node, java.util.Map<String, String> map, JdbcTemplate jdbc) {
    if (node == null) return;
    String type = node.getString("type");
    if ("base-data".equals(type)) {
      String dbField = node.getString("dbField");
      String refPageId = node.getString("refPageId");
      if (refPageId == null || refPageId.isEmpty()) {
        JSONObject props = node.getJSONObject("props");
        refPageId = props != null ? props.getString("refPageId") : null;
      }
      log.info("[TableSync] 扫描 base-data: dbField={}, refPageId={}", dbField, refPageId);
      if (dbField != null && !dbField.trim().isEmpty() && refPageId != null && !refPageId.isEmpty()) {
        String colType = lookupRefPageKeyType(refPageId, jdbc);
        if (colType != null) {
          map.put(dbField.trim().toUpperCase(), colType);
          log.info("[TableSync] 基础资料关联字段 {} → 类型 {} (refPageId={})", dbField, colType, refPageId);
        } else {
          log.warn("[TableSync] 基础资料关联字段 {} 未找到 refPageId={} 的主键类型，保持默认类型", dbField, refPageId);
        }
      }
    }
    JSONArray children = node.getJSONArray("children");
    if (children != null) {
      for (int i = 0; i < children.size(); i++) {
        scanBaseDataNodes(children.getJSONObject(i), map, jdbc);
      }
    }
  }

  private String lookupRefPageKeyType(String refPageId, JdbcTemplate jdbc) {
    try {
      List<String> rows = jdbc.queryForList(
          "SELECT schema_json FROM lowcode_page WHERE page_id = ?", String.class, refPageId);
      if (rows.isEmpty()) {
        log.warn("[TableSync] lowcode_page 中未找到 page_id={}", refPageId);
        return null;
      }
      JSONObject pageSchema = JSONObject.parseObject(rows.get(0));
      String refTableName = pageSchema.getString("tableName");
      String refPkField = pageSchema.getString("primaryKeyField");
      String keyStrategy = pageSchema.getString("keyStrategy");
      if (refPkField == null || refPkField.trim().isEmpty()) refPkField = "id";
      log.info("[TableSync] refPageId={} tableName={} pkField={} keyStrategy={}", refPageId, refTableName, refPkField, keyStrategy);

      if ("SNOWFLAKE".equalsIgnoreCase(keyStrategy)) return "BIGINT";
      if (keyStrategy != null && !keyStrategy.trim().isEmpty()) return "VARCHAR(64)";

      if (refTableName != null && !refTableName.trim().isEmpty()) {
        String dbColType = queryColumnDbType(jdbc, refTableName.trim(), refPkField.trim());
        if (dbColType != null) {
          String mapped = mapDbTypeToColumnType(dbColType);
          log.info("[TableSync] refPageId={} keyStrategy为空, 回退到实际列类型: {} → {}", refPageId, dbColType, mapped);
          return mapped;
        }
      }
      return "VARCHAR(64)";
    } catch (Exception e) {
      log.warn("[TableSync] 查询关联页面主键类型失败: refPageId={}, err={}", refPageId, e.getMessage());
    }
    return null;
  }

  /** 查询数据库中某表某列的实际 data_type */
  private String queryColumnDbType(JdbcTemplate jdbc, String tableName, String columnName) {
    try {
      List<String> rows = jdbc.queryForList(
          "SELECT data_type FROM information_schema.columns "
          + "WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?) "
          + "AND table_schema = (SELECT current_schema())",
          String.class, tableName, columnName);
      if (!rows.isEmpty()) return rows.get(0);
    } catch (Exception e) {
      log.warn("[TableSync] 查询列类型失败: {}.{} : {}", tableName, columnName, e.getMessage());
    }
    return null;
  }

  /** PostgreSQL data_type → 建表列类型 */
  private String mapDbTypeToColumnType(String dbType) {
    String t = dbType.toLowerCase().trim();
    if (t.contains("bigint") || t.contains("int8")) return "BIGINT";
    if (t.contains("integer") || t.contains("int4") || t.contains("smallint") || t.contains("int2")) return "INTEGER";
    if (t.contains("timestamp")) return "TIMESTAMP";
    if (t.contains("boolean") || t.contains("bool")) return "BOOLEAN";
    if (t.contains("numeric") || t.contains("decimal")) return "NUMERIC(18,6)";
    if (t.contains("text")) return "TEXT";
    if (t.contains("character varying") || t.contains("varchar")) return "VARCHAR(255)";
    if (t.contains("char")) return "VARCHAR(255)";
    return "VARCHAR(255)";
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
      String label = node.getString("label");
      fields.add(new FieldDef(dbField.trim(), columnType, label));
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

  /** 检测表是否存在（通过方言提供的元数据查询 SQL） */
  private boolean tableExists(JdbcTemplate jdbc, String tableName, SqlDialect dialect) {
    String sql = dialect.checkTableExistsSql();
    List<Integer> rows = jdbc.queryForList(sql, Integer.class, tableName);
    return !rows.isEmpty();
  }

  /** 建表：CREATE TABLE + 所有字段，标识符通过方言引用 */
  private void createTable(JdbcTemplate jdbc, String tableName, List<FieldDef> fields, SqlDialect dialect, String primaryKeyField, boolean useSnowflake, String tableComment) {
    String qTable = dialect.quoteIdentifier(tableName);
    StringBuilder sb = new StringBuilder();
    sb.append("CREATE TABLE ").append(qTable).append(" (");
    // 主键字段：用户配置的自定义主键优先，否则默认 id
    if (!"id".equalsIgnoreCase(primaryKeyField)) {
      FieldDef pkFieldDef = null;
      for (FieldDef f : fields) {
        if (f.name.equalsIgnoreCase(primaryKeyField)) { pkFieldDef = f; break; }
      }
      String pkType = useSnowflake ? "BIGINT" : (pkFieldDef != null ? pkFieldDef.type : "VARCHAR(64)");
      sb.append("\n  ").append(dialect.quoteIdentifier(primaryKeyField)).append(" ").append(pkType).append(" PRIMARY KEY NOT NULL");
    } else {
      String pkType = useSnowflake ? "BIGINT" : "VARCHAR(64)";
      sb.append("\n  ").append(dialect.quoteIdentifier("id")).append(" ").append(pkType).append(" PRIMARY KEY");
    }
    sb.append(",\n  ").append(dialect.quoteIdentifier("created_at")).append(" TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
    sb.append(",\n  ").append(dialect.quoteIdentifier("updated_at")).append(" TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
    sb.append(",\n  ").append(dialect.quoteIdentifier("created_by")).append(" VARCHAR(64)");
    sb.append(",\n  ").append(dialect.quoteIdentifier("updated_by")).append(" VARCHAR(64)");
    sb.append(",\n  ").append(dialect.quoteIdentifier("deleted")).append(" BOOLEAN DEFAULT FALSE");
    sb.append(",\n  ").append(dialect.quoteIdentifier("status")).append(" INTEGER DEFAULT 1");
    for (FieldDef f : fields) {
      if (f.name.equalsIgnoreCase(primaryKeyField)) continue;
      sb.append(",\n  ").append(dialect.quoteIdentifier(f.name)).append(" ").append(f.type);
    }
    sb.append("\n)");
    log.info("[TableSync] 建表: {}", tableName);
    jdbc.execute(sb.toString());
    addComments(jdbc, tableName, fields, dialect, tableComment);
  }

  /** 添加表注释和列注释（COMMENT ON TABLE/COLUMN，兼容 PG/Oracle/达梦/电科金仓） */
  private void addComments(JdbcTemplate jdbc, String tableName, List<FieldDef> fields, SqlDialect dialect, String tableComment) {
    String qTable = dialect.quoteIdentifier(tableName);
    if (tableComment != null && !tableComment.trim().isEmpty()) {
      try {
        jdbc.execute("COMMENT ON TABLE " + qTable + " IS '" + escapeComment(tableComment) + "'");
      } catch (Exception e) {
        log.warn("[TableSync] 添加表注释失败: {}", e.getMessage());
      }
    }
    for (FieldDef f : fields) {
      if (f.label != null && !f.label.trim().isEmpty()) {
        try {
          jdbc.execute("COMMENT ON COLUMN " + qTable + "." + dialect.quoteIdentifier(f.name) + " IS '" + escapeComment(f.label) + "'");
        } catch (Exception e) {
          log.warn("[TableSync] 添加列注释 {}.{} 失败: {}", tableName, f.name, e.getMessage());
        }
      }
    }
  }

  /** 转义注释中的单引号 */
  private String escapeComment(String comment) {
    return comment.replace("'", "''");
  }

  /** 检测并添加缺失的字段，返回新增字段数 */
  private int addMissingColumns(JdbcTemplate jdbc, String tableName, List<FieldDef> fields, SqlDialect dialect) {
    Set<String> existingCols = getExistingColumns(jdbc, tableName, dialect);
    java.util.Map<String, String> existingColTypes = getExistingColumnTypes(jdbc, tableName, dialect);
    int added = 0;
    String qTable = dialect.quoteIdentifier(tableName);
    for (FieldDef f : fields) {
      String qCol = dialect.quoteIdentifier(f.name);
      if (!existingCols.contains(f.name.toLowerCase())) {
        String sql = "ALTER TABLE " + qTable + " ADD COLUMN " + qCol + " " + f.type;
        log.info("[TableSync] 加字段: {}.{} {}", tableName, f.name, f.type);
        jdbc.execute(sql);
        if (f.label != null && !f.label.trim().isEmpty()) {
          try {
            jdbc.execute("COMMENT ON COLUMN " + qTable + "." + qCol + " IS '" + escapeComment(f.label) + "'");
          } catch (Exception e) {
            log.warn("[TableSync] 添加列注释 {}.{} 失败: {}", tableName, f.name, e.getMessage());
          }
        }
        added++;
      } else {
        String currentType = existingColTypes.get(f.name.toLowerCase());
        if (currentType != null && needsTypeChange(currentType, f.type)) {
          log.info("[TableSync] 改列类型: {}.{} {} → {}", tableName, f.name, currentType, f.type);
          try {
            String alterSql = buildAlterColumnTypeSql(qTable, qCol, f.type);
            jdbc.execute(alterSql);
            added++;
          } catch (Exception e) {
            log.error("[TableSync] 改列类型失败: {}.{} {} → {}: {}", tableName, f.name, currentType, f.type, e.getMessage());
          }
        }
      }
    }
    return added;
  }

  /** 确保表有 status 字段（启用/禁用功能用），不存在则添加 INTEGER DEFAULT 1 */
  private boolean ensureStatusColumn(JdbcTemplate jdbc, String tableName, SqlDialect dialect) {
    Set<String> existingCols = getExistingColumns(jdbc, tableName, dialect);
    if (existingCols.contains("status")) return false;
    String qTable = dialect.quoteIdentifier(tableName);
    String qCol = dialect.quoteIdentifier("status");
    try {
      jdbc.execute("ALTER TABLE " + qTable + " ADD COLUMN " + qCol + " INTEGER DEFAULT 1");
      log.info("[TableSync] 自动添加 status 字段: {}.{} INTEGER DEFAULT 1", tableName, "status");
      return true;
    } catch (Exception e) {
      log.warn("[TableSync] 添加 status 字段失败: {}.{}: {}", tableName, "status", e.getMessage());
      return false;
    }
  }

  /** 判断是否需要修改列类型 */
  private boolean needsTypeChange(String currentType, String desiredType) {
    String cur = currentType.toLowerCase().trim();
    String des = desiredType.toLowerCase().trim();
    if (des.contains("bigint")) {
      return cur.contains("character varying") || cur.contains("varchar") || cur.contains("text");
    }
    if (des.contains("varchar") || des.contains("text")) {
      return cur.contains("bigint") || cur.contains("integer") || cur.contains("int");
    }
    return false;
  }

  /** 构建 ALTER COLUMN TYPE SQL（PostgreSQL 语法） */
  private String buildAlterColumnTypeSql(String qTable, String qCol, String newType) {
    String baseType = newType.replaceAll("\\(.*\\)", "").trim().toUpperCase();
    if ("BIGINT".equals(baseType)) {
      return "ALTER TABLE " + qTable + " ALTER COLUMN " + qCol + " TYPE BIGINT USING " + qCol + "::bigint";
    }
    if ("INTEGER".equals(baseType)) {
      return "ALTER TABLE " + qTable + " ALTER COLUMN " + qCol + " TYPE INTEGER USING " + qCol + "::integer";
    }
    if ("TEXT".equals(baseType)) {
      return "ALTER TABLE " + qTable + " ALTER COLUMN " + qCol + " TYPE TEXT USING " + qCol + "::text";
    }
    return "ALTER TABLE " + qTable + " ALTER COLUMN " + qCol + " TYPE " + newType + " USING " + qCol + "::varchar";
  }

  /** 获取表已有字段的类型映射（列名小写 → data_type） */
  private java.util.Map<String, String> getExistingColumnTypes(JdbcTemplate jdbc, String tableName, SqlDialect dialect) {
    java.util.Map<String, String> result = new java.util.HashMap<>();
    try {
      String sql = "SELECT column_name, data_type FROM information_schema.columns "
          + "WHERE LOWER(table_name) = LOWER(?) "
          + "AND table_schema = (SELECT current_schema())";
      jdbc.query(sql, rs -> {
        result.put(rs.getString("column_name").toLowerCase(), rs.getString("data_type"));
      }, tableName);
    } catch (Exception e) {
      log.warn("[TableSync] 获取列类型失败: {}.{}", tableName, e.getMessage());
    }
    return result;
  }

  /** 获取表已有的字段列表（通过方言提供的元数据查询 SQL） */
  private Set<String> getExistingColumns(JdbcTemplate jdbc, String tableName, SqlDialect dialect) {
    String sql = dialect.queryTableColumnsSql();
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

    MultiDataSourceHolder.setDatasource(DB_NAME);
    try {
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      SqlDialect dialect = dialect();
      if (!tableExists(jdbc, tableName, dialect)) {
        return result;
      }

      String colSql = dialect.queryTableColumnsSql();
      List<String> dbCols = jdbc.queryForList(colSql, String.class, tableName);

      Set<String> schemaCols = new HashSet<>();
      if (schemaFieldNames != null) {
        for (String f : schemaFieldNames) {
          if (f != null) schemaCols.add(f.toLowerCase());
        }
      }

      String qTable = dialect.quoteIdentifier(tableName);
      for (String col : dbCols) {
        if (SYSTEM_COLUMNS.contains(col.toLowerCase())) continue;
        if (schemaCols.contains(col.toLowerCase())) continue;

        JSONObject item = new JSONObject();
        item.put("name", col);
        String checkSql = "SELECT COUNT(*) FROM " + qTable + " WHERE " + dialect.quoteIdentifier(col) + " IS NOT NULL";
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

    MultiDataSourceHolder.setDatasource(DB_NAME);
    try {
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      SqlDialect dialect = dialect();

      if (!tableExists(jdbc, tableName, dialect)) {
        throw new IllegalArgumentException("表 " + tableName + " 不存在");
      }

      String qTable = dialect.quoteIdentifier(tableName);
      String qCol = dialect.quoteIdentifier(columnName);
      String checkSql = "SELECT COUNT(*) FROM " + qTable + " WHERE " + qCol + " IS NOT NULL";
      Long count = jdbc.queryForObject(checkSql, Long.class);
      if (count != null && count > 0) {
        throw new IllegalStateException("字段 " + columnName + " 有 " + count + " 条非空数据，不允许删除");
      }

      String sql = "ALTER TABLE " + qTable + " DROP COLUMN " + qCol;
      log.info("[TableSync] 删字段: {}.{}", tableName, columnName);
      jdbc.execute(sql);
    } finally {
      MultiDataSourceHolder.clearDataSource();
    }
  }

  // ==================== 明细表同步 ====================

  /**
   * 同步明细表：根据页面 schema 中 data-grid 组件的配置建明细表。
   * 仅在表单页面保存时调用。
   *
   * @param schema 页面 schema JSON
   * @return 同步结果消息（null 表示无明细表配置）
   */
  public String syncDetailTables(JSONObject schema) {
    List<DetailTableDef> detailTables = new ArrayList<>();
    // root 兼容对象与数组两种形态（设计器实际存储为数组）
    Object rootObj = schema.get("root");
    if (rootObj instanceof JSONObject) {
      collectDetailTables((JSONObject) rootObj, detailTables);
    } else if (rootObj instanceof JSONArray) {
      JSONArray rootArr = (JSONArray) rootObj;
      for (int i = 0; i < rootArr.size(); i++) {
        collectDetailTables(rootArr.getJSONObject(i), detailTables);
      }
    }
    if (detailTables.isEmpty()) return null;

    MultiDataSourceHolder.setDatasource(DB_NAME);
    try {
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      SqlDialect dialect = dialect();

      Set<String> builtTables = new HashSet<>();
      String mainTableName = schema.getString("tableName");
      if (mainTableName != null && !mainTableName.trim().isEmpty()) {
        builtTables.add(mainTableName.trim().toLowerCase());
      }

      StringBuilder msg = new StringBuilder();
      int maxIterations = detailTables.size() + 1;
      for (int iter = 0; iter < maxIterations; iter++) {
        boolean anyBuilt = false;
        for (DetailTableDef dt : detailTables) {
          if (dt.built) continue;

          String parentTableName = resolveParentTableName(dt, schema, jdbc, dialect);
          if (parentTableName == null) {
            dt.error = "无法解析父表 (parentType=" + dt.parentType + ", parentRef=" + dt.parentRef + ")";
            continue;
          }
          if (!builtTables.contains(parentTableName.toLowerCase())
              && !tableExists(jdbc, parentTableName, dialect)) {
            continue;
          }

          String parentPkField = resolveParentPkField(dt, schema, jdbc);
          if (parentPkField == null || parentPkField.trim().isEmpty()) parentPkField = "id";

          String parentPkType = resolveParentPkType(dt, schema, jdbc, dialect);
          if (parentPkType == null) parentPkType = "VARCHAR(64)";

          List<FieldDef> fields = new ArrayList<>();
          if (!parentPkField.equalsIgnoreCase(dt.primaryKeyField)) {
            fields.add(new FieldDef(parentPkField, parentPkType, "关联外键"));
          }
          String pagePkField = schema.getString("primaryKeyField");
          if (pagePkField == null || pagePkField.trim().isEmpty()) pagePkField = "id";
          String pageKeyStrategy = schema.getString("keyStrategy");
          String pagePkType = ("SNOWFLAKE".equalsIgnoreCase(pageKeyStrategy)) ? "BIGINT" : "VARCHAR(64)";
          boolean parentIsPage = dt.parentType == null || dt.parentType.trim().isEmpty() || "page".equals(dt.parentType);
          if (!parentIsPage
              && !pagePkField.equalsIgnoreCase(dt.primaryKeyField)
              && !pagePkField.equalsIgnoreCase(parentPkField)) {
            fields.add(new FieldDef(pagePkField, pagePkType, "页面主键关联"));
          }
          for (ColumnDef col : dt.columns) {
            if (!col.dataField.equalsIgnoreCase(parentPkField)
                && !col.dataField.equalsIgnoreCase(dt.primaryKeyField)
                && isValidIdentifier(col.dataField)) {
              String colType = COLUMN_TYPE_MAP.getOrDefault(col.dataType, DEFAULT_COLUMN_TYPE);
              fields.add(new FieldDef(col.dataField, colType, col.caption));
            }
          }

          if (!isValidIdentifier(dt.tableName)) {
            dt.error = "表名不合法";
            continue;
          }

          if (!tableExists(jdbc, dt.tableName, dialect)) {
            boolean dtUseSnowflake = "SNOWFLAKE".equalsIgnoreCase(dt.keyStrategy);
            createTable(jdbc, dt.tableName, fields, dialect, dt.primaryKeyField, dtUseSnowflake, dt.tableComment);
            sqlFieldValidator.evictCache(DB_NAME, dt.tableName);
            msg.append("已创建明细表 ").append(dt.tableName).append("，字段数 ").append(fields.size()).append("；");
          } else {
            int added = addMissingColumns(jdbc, dt.tableName, fields, dialect);
            if (added > 0) sqlFieldValidator.evictCache(DB_NAME, dt.tableName);
            msg.append("明细表 ").append(dt.tableName).append(added > 0 ? "已添加" + added + "个字段" : "已是最新").append("；");
          }

          dt.built = true;
          builtTables.add(dt.tableName.toLowerCase());
          anyBuilt = true;
        }
        if (!anyBuilt) break;
      }

      for (DetailTableDef dt : detailTables) {
        if (!dt.built) {
          msg.append("明细表 ").append(dt.tableName).append("建表失败：").append(dt.error != null ? dt.error : "父表未建").append("；");
          log.warn("[TableSync] 明细表 {} 建表失败: {}", dt.tableName, dt.error);
        }
      }

      return msg.length() > 0 ? msg.toString() : null;
    } catch (Exception e) {
      log.error("[TableSync] 同步明细表失败: {}", e.getMessage(), e);
      return "明细表同步失败: " + e.getMessage();
    } finally {
      MultiDataSourceHolder.clearDataSource();
    }
  }

  /** 递归收集 schema 节点树中所有 type=data-grid 且配置了 tableName 的组件 */
  private void collectDetailTables(JSONObject node, List<DetailTableDef> result) {
    if (node == null) return;
    String type = node.getString("type");
    if ("data-grid".equals(type)) {
      JSONObject props = node.getJSONObject("props");
      if (props != null) {
        String tableName = props.getString("tableName");
        if (tableName != null && !tableName.trim().isEmpty()) {
          DetailTableDef dt = new DetailTableDef();
          dt.tableName = tableName.trim();
          dt.tableComment = props.getString("tableComment");
          String pkField = props.getString("keyExpr");
          dt.primaryKeyField = (pkField != null && !pkField.trim().isEmpty()) ? pkField.trim() : "id";
          String keyStrategy = props.getString("keyStrategy");
          dt.keyStrategy = (keyStrategy != null && !keyStrategy.trim().isEmpty()) ? keyStrategy.trim() : "SNOWFLAKE";
          dt.parentType = props.getString("parentType");
          dt.parentRef = props.getString("parentRef");

          JSONArray columns = props.getJSONArray("columns");
          if (columns != null) {
            for (int i = 0; i < columns.size(); i++) {
              JSONObject col = columns.getJSONObject(i);
              String dataField = col.getString("dataField");
              if (dataField != null && !dataField.trim().isEmpty()) {
                ColumnDef cd = new ColumnDef();
                cd.dataField = dataField.trim();
                cd.caption = col.getString("caption");
                cd.dataType = col.getString("dataType");
                dt.columns.add(cd);
              }
            }
          }
          result.add(dt);
          log.info("[TableSync] 发现明细表配置: {} pkField={} parentType={} parentRef={} 列数={}",
              dt.tableName, dt.primaryKeyField, dt.parentType, dt.parentRef, dt.columns.size());
        }
      }
    }
    JSONArray children = node.getJSONArray("children");
    if (children != null) {
      for (int i = 0; i < children.size(); i++) {
        collectDetailTables(children.getJSONObject(i), result);
      }
    }
  }

  /** 按 ID 递归查找 schema 节点 */
  private JSONObject findNodeById(JSONObject node, String id) {
    if (node == null || id == null) return null;
    if (id.equals(node.getString("id"))) return node;
    JSONArray children = node.getJSONArray("children");
    if (children != null) {
      for (int i = 0; i < children.size(); i++) {
        JSONObject found = findNodeById(children.getJSONObject(i), id);
        if (found != null) return found;
      }
    }
    return null;
  }

  /** 解析父表名 */
  private String resolveParentTableName(DetailTableDef dt, JSONObject schema, JdbcTemplate jdbc, SqlDialect dialect) {
    String parentType = dt.parentType;
    String parentRef = dt.parentRef;
    if (parentType == null || parentType.trim().isEmpty() || "page".equals(parentType)) {
      if (parentRef == null || parentRef.trim().isEmpty()) {
        return schema.getString("tableName");
      }
      try {
        List<String> rows = jdbc.queryForList(
            "SELECT schema_json FROM lowcode_page WHERE page_id = ?", String.class, parentRef.trim());
        if (!rows.isEmpty()) {
          JSONObject pageSchema = JSONObject.parseObject(rows.get(0));
          return pageSchema.getString("tableName");
        }
      } catch (Exception e) {
        log.warn("[TableSync] 查询父页面表名失败: parentRef={}", parentRef);
      }
      return null;
    }
    if ("table".equals(parentType) && parentRef != null && !parentRef.trim().isEmpty()) {
      JSONObject root = schema.getJSONObject("root");
      JSONObject node = findNodeById(root, parentRef.trim());
      if (node != null) {
        JSONObject props = node.getJSONObject("props");
        if (props != null) return props.getString("tableName");
      }
    }
    return null;
  }

  /** 解析父表主键字段名 */
  private String resolveParentPkField(DetailTableDef dt, JSONObject schema, JdbcTemplate jdbc) {
    String parentType = dt.parentType;
    String parentRef = dt.parentRef;
    if (parentType == null || parentType.trim().isEmpty() || "page".equals(parentType)) {
      if (parentRef == null || parentRef.trim().isEmpty()) {
        String pk = schema.getString("primaryKeyField");
        return (pk != null && !pk.trim().isEmpty()) ? pk.trim() : "id";
      }
      try {
        List<String> rows = jdbc.queryForList(
            "SELECT schema_json FROM lowcode_page WHERE page_id = ?", String.class, parentRef.trim());
        if (!rows.isEmpty()) {
          JSONObject pageSchema = JSONObject.parseObject(rows.get(0));
          String pk = pageSchema.getString("primaryKeyField");
          return (pk != null && !pk.trim().isEmpty()) ? pk.trim() : "id";
        }
      } catch (Exception e) {
        log.warn("[TableSync] 查询父页面主键字段失败: parentRef={}", parentRef);
      }
      return "id";
    }
    if ("table".equals(parentType) && parentRef != null && !parentRef.trim().isEmpty()) {
      JSONObject root = schema.getJSONObject("root");
      JSONObject node = findNodeById(root, parentRef.trim());
      if (node != null) {
        JSONObject props = node.getJSONObject("props");
        if (props != null) {
          String pk = props.getString("keyExpr");
          return (pk != null && !pk.trim().isEmpty()) ? pk.trim() : "id";
        }
      }
    }
    return "id";
  }

  /** 解析父表主键类型 */
  private String resolveParentPkType(DetailTableDef dt, JSONObject schema, JdbcTemplate jdbc, SqlDialect dialect) {
    String parentTableName = resolveParentTableName(dt, schema, jdbc, dialect);
    String parentPkField = resolveParentPkField(dt, schema, jdbc);
    if (parentTableName == null || parentPkField == null) return "VARCHAR(64)";

    String parentType = dt.parentType;
    String parentRef = dt.parentRef;
    if (parentType == null || parentType.trim().isEmpty() || "page".equals(parentType)) {
      if (parentRef != null && !parentRef.trim().isEmpty()) {
        try {
          List<String> rows = jdbc.queryForList(
              "SELECT schema_json FROM lowcode_page WHERE page_id = ?", String.class, parentRef.trim());
          if (!rows.isEmpty()) {
            JSONObject pageSchema = JSONObject.parseObject(rows.get(0));
            String keyStrategy = pageSchema.getString("keyStrategy");
            if ("SNOWFLAKE".equalsIgnoreCase(keyStrategy)) return "BIGINT";
            if (keyStrategy != null && !keyStrategy.trim().isEmpty()) return "VARCHAR(64)";
          }
        } catch (Exception e) {
          log.warn("[TableSync] 查询父页面主键类型失败: parentRef={}", parentRef);
        }
      } else {
        String keyStrategy = schema.getString("keyStrategy");
        if ("SNOWFLAKE".equalsIgnoreCase(keyStrategy)) return "BIGINT";
        if (keyStrategy != null && !keyStrategy.trim().isEmpty()) return "VARCHAR(64)";
      }
    }

    String dbColType = queryColumnDbType(jdbc, parentTableName.trim(), parentPkField.trim());
    if (dbColType != null) {
      return mapDbTypeToColumnType(dbColType);
    }
    return "VARCHAR(64)";
  }

  /** 明细表定义 */
  private static class DetailTableDef {
    String tableName;
    String tableComment;
    String primaryKeyField = "id";
    String keyStrategy = "SNOWFLAKE";
    String parentType;
    String parentRef;
    List<ColumnDef> columns = new ArrayList<>();
    boolean built = false;
    String error;
  }

  /** 列定义（明细表用） */
  private static class ColumnDef {
    String dataField;
    String caption;
    String dataType;
  }

  /** 字段定义 */
  private static class FieldDef {
    final String name;
    String type;
    final String label;

    FieldDef(String name, String type, String label) {
      this.name = name;
      this.type = type;
      this.label = label;
    }
  }
}