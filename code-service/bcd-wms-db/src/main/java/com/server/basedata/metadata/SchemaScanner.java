package com.server.basedata.metadata;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.dialect.SqlDialect;
import com.server.sqlengine.dialect.SqlDialectRegistry;
import com.server.sqlengine.executor.SqlExecutor;
import com.server.sqlengine.model.CompiledSql;
import com.server.sqlengine.model.SqlParam;

import java.util.ArrayList;
import java.util.List;

/**
 * Schema 扫描器。
 * <p>
 * 通过 {@link SqlDialect#queryTableSchemaSql()} 获取各方言的表结构查询 SQL，
 * 扫描指定表的实际列结构信息（列名、类型、长度、精度、是否可空等），
 * 供 {@link SchemaHashBuilder} 计算结构哈希、{@link SchemaDiff} 计算结构差异，
 * 用于检测表结构变更并触发元数据重新编译。
 */
public class SchemaScanner {

  /** SQL 方言注册表 */
  private final SqlDialectRegistry dialectRegistry;
  /** SQL 执行器 */
  private final SqlExecutor sqlExecutor;

  /**
   * 构造扫描器。
   *
   * @param dialectRegistry SQL 方言注册表
   * @param sqlExecutor     SQL 执行器
   */
  public SchemaScanner(SqlDialectRegistry dialectRegistry, SqlExecutor sqlExecutor) {
    this.dialectRegistry = dialectRegistry;
    this.sqlExecutor = sqlExecutor;
  }

  /**
   * 列结构信息。
   */
  public static final class ColumnInfo {
    /** 列名 */
    private final String columnName;
    /** 数据类型 */
    private final String dataType;
    /** 字符最大长度 */
    private final Integer dataLength;
    /** 数值精度 */
    private final Integer dataPrecision;
    /** 数值标度 */
    private final Integer dataScale;
    /** 是否可空 */
    private final boolean nullable;

    /**
     * 构造列信息。
     *
     * @param columnName    列名
     * @param dataType      数据类型
     * @param dataLength    字符最大长度
     * @param dataPrecision 数值精度
     * @param dataScale     数值标度
     * @param nullable      是否可空
     */
    public ColumnInfo(String columnName, String dataType, Integer dataLength,
        Integer dataPrecision, Integer dataScale, boolean nullable) {
      this.columnName = columnName;
      this.dataType = dataType;
      this.dataLength = dataLength;
      this.dataPrecision = dataPrecision;
      this.dataScale = dataScale;
      this.nullable = nullable;
    }

    /**
     * 获取列名。
     *
     * @return 列名
     */
    public String getColumnName() {
      return columnName;
    }

    /**
     * 获取数据类型。
     *
     * @return 数据类型
     */
    public String getDataType() {
      return dataType;
    }

    /**
     * 获取字符最大长度。
     *
     * @return 字符最大长度，可能为 null
     */
    public Integer getDataLength() {
      return dataLength;
    }

    /**
     * 获取数值精度。
     *
     * @return 数值精度，可能为 null
     */
    public Integer getDataPrecision() {
      return dataPrecision;
    }

    /**
     * 获取数值标度。
     *
     * @return 数值标度，可能为 null
     */
    public Integer getDataScale() {
      return dataScale;
    }

    /**
     * 判断是否可空。
     *
     * @return 可空返回 true
     */
    public boolean isNullable() {
      return nullable;
    }
  }

  /**
   * 扫描指定表的列结构信息。
   * <p>
   * 通过 {@link SqlDialect#queryTableSchemaSql()} 获取各方言的表结构查询 SQL，
   * 参数化执行，不硬编码数据库特有语法。结果集列名通过 AS 别名统一，
   * SqlExecutor 大写化后 key 为
   * COLUMN_NAME/DATA_TYPE/DATA_LENGTH/DATA_PRECISION/DATA_SCALE/IS_NULLABLE。
   *
   * @param tableName 表名
   * @return 列信息列表
   * @throws Exception SQL 异常
   */
  public List<ColumnInfo> scanTable(String tableName) throws Exception {
    List<ColumnInfo> result = new ArrayList<ColumnInfo>();
    // 通过方言获取表结构查询 SQL，不硬编码 PG/Oracle 特有语法
    SqlDialect dialect = dialectRegistry.dialect(null);
    String sql = dialect.queryTableSchemaSql();
    String schemaTableName = dialect.normalizeSchemaIdentifier(tableName);

    List<SqlParam> params = new ArrayList<SqlParam>();
    params.add(new SqlParam("table_name", schemaTableName));
    CompiledSql compiled = new CompiledSql(sql, params);

    JSONArray rows = sqlExecutor.query("lowcode", compiled);
    for (int i = 0; i < rows.size(); i++) {
      JSONObject row = rows.getJSONObject(i);
      // 方言 SQL 通过 AS 别名统一了列名，SqlExecutor 大写化后 key 为大写
      String columnName = row.getString("COLUMN_NAME");
      String dataType = row.getString("DATA_TYPE");
      Integer dataLength = row.getInteger("DATA_LENGTH");
      Integer dataPrecision = row.getInteger("DATA_PRECISION");
      Integer dataScale = row.getInteger("DATA_SCALE");
      String isNullable = row.getString("IS_NULLABLE");
      // PG/SQL Server: 'YES'/'NO' ; Oracle/DM: 'Y'/'N'
      boolean nullable = "YES".equalsIgnoreCase(isNullable) || "Y".equalsIgnoreCase(isNullable);
      ColumnInfo info = new ColumnInfo(columnName, dataType, dataLength,
          dataPrecision, dataScale, nullable);
      result.add(info);
    }
    return result;
  }
}
