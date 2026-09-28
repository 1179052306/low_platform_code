package com.server.basedata.database;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.SqlEngine;
import com.server.sqlengine.executor.SqlExecutor;
import com.server.sqlengine.model.CompiledSql;
import com.server.sqlengine.model.Condition;
import com.server.sqlengine.model.SelectQuery;
import com.server.sqlengine.validator.SqlFieldValidator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IN 分批查询策略 — 直接复用 sqlengine，不自己写 JDBC。
 * <p>
 * 当按主键集合批量回源查询时，若主键数量过大，单条 SQL 的 IN 子句可能超出数据库参数上限
 * 或导致查询计划劣化。该策略将主键集合按 batchSize 切分为多个子批次，逐批通过
 * SqlEngine 编译 + SqlExecutor 执行，合并结果后返回。
 * </p>
 * <p>
 * 数据库类型由 sqlEngine 底层决定（DbConfig/HikariDataSource），本类不关心具体是 PostgreSQL 还是
 * Oracle。
 * </p>
 */
public class BatchQueryStrategy {

  /** 默认每批大小，兼顾单批查询开销与 IN 子句长度限制 */
  private static final int DEFAULT_BATCH_SIZE = 1000;

  /** SQL 编译入口：SelectQuery → CompiledSql */
  private final SqlEngine sqlEngine;

  /** SQL 执行入口：CompiledSql → JSONArray */
  private final SqlExecutor sqlExecutor;

  /** SQL 字段校验器：白名单 + 元数据双重校验，防止 SQL 注入 */
  private final SqlFieldValidator fieldValidator;

  /** 每批最大主键数量 */
  private final int batchSize;

  /**
   * 使用默认批大小（1000）构造。
   *
   * @param sqlEngine      SQL 编译入口
   * @param sqlExecutor    SQL 执行入口
   * @param fieldValidator SQL 字段校验器
   */
  public BatchQueryStrategy(SqlEngine sqlEngine, SqlExecutor sqlExecutor,
      SqlFieldValidator fieldValidator) {
    this(sqlEngine, sqlExecutor, fieldValidator, DEFAULT_BATCH_SIZE);
  }

  /**
   * 指定批大小构造。
   *
   * @param sqlEngine      SQL 编译入口
   * @param sqlExecutor    SQL 执行入口
   * @param fieldValidator SQL 字段校验器
   * @param batchSize      每批最大主键数量
   */
  public BatchQueryStrategy(SqlEngine sqlEngine, SqlExecutor sqlExecutor,
      SqlFieldValidator fieldValidator, int batchSize) {
    this.sqlEngine = sqlEngine;
    this.sqlExecutor = sqlExecutor;
    this.fieldValidator = fieldValidator;
    this.batchSize = batchSize;
  }

  /**
   * 按主键集合分批查询表数据。
   * <p>
   * 将 ids 切分为若干子列表，逐批构建 SelectQuery → SqlEngine.select() → SqlExecutor.query()，
   * 将 JSONArray 转为 Map<Object, Object[]>（主键 → 字段值数组）后合并返回。
   * </p>
   *
   * @param tableName  表名
   * @param idColumn   主键列名
   * @param fieldNames 待查询字段名数组
   * @param ids        主键集合
   * @return 以主键为 key、字段值数组为 value 的映射
   * @throws Exception 查询过程中抛出的异常
   */
  public Map<Object, Object[]> batchQuery(String tableName, String idColumn,
      String[] fieldNames, Collection<?> ids) throws Exception {

    // 校验表名 + 列名（idColumn + fieldNames）合法性，防止 SQL 注入
    List<String> allColumns = new ArrayList<String>(fieldNames.length + 1);
    allColumns.add(idColumn);
    allColumns.addAll(Arrays.asList(fieldNames));
    fieldValidator.validateTableAndColumns("lowcode", tableName, allColumns);

    Map<Object, Object[]> result = new HashMap<Object, Object[]>();
    List<Object> idList = new ArrayList<Object>(ids);

    // 按 batchSize 滑动切分主键集合，逐批查询
    for (int offset = 0; offset < idList.size(); offset += batchSize) {
      int end = Math.min(offset + batchSize, idList.size());
      List<Object> subList = idList.subList(offset, end);

      // 1. 构建 SelectQuery：SELECT idColumn, field1, field2, ... FROM tableName WHERE
      // idColumn IN (?, ...)
      SelectQuery.Builder builder = SelectQuery.builder()
          .from(tableName)
          .column(idColumn);
      for (String field : fieldNames) {
        builder.column(field);
      }
      builder.where(Condition.in(idColumn, subList));
      SelectQuery query = builder.build();

      // 2. 编译为参数化 SQL
      CompiledSql compiled = sqlEngine.select(query, "lowcode");

      // 3. 执行查询，返回 JSONArray（每行一个 JSONObject）
      JSONArray rows = sqlExecutor.query("lowcode", compiled);

      // 4. 转换为 Map<Object, Object[]> 并合并到结果
      for (int i = 0; i < rows.size(); i++) {
        JSONObject row = rows.getJSONObject(i);
        Object id = getValueIgnoreCase(row, idColumn);
        Object[] values = new Object[fieldNames.length];
        for (int j = 0; j < fieldNames.length; j++) {
          values[j] = getValueIgnoreCase(row, fieldNames[j]);
        }
        result.put(id, values);
      }
    }
    return result;
  }

  /**
   * 从 JSONObject 取值（元数据列名与查询结果 key 均为大写）。
   * <p>
   * SqlExecutor 已统一将 ResultSet 列名转大写，元数据中的列名也为大写，
   * 故 {@code row.get(key)} 可直接命中。保留 fallback 仅为防御性兼容。
   * </p>
   *
   * @param row 查询返回的行
   * @param key 元数据中的列名（大写）
   * @return 对应字段值；不存在返回 null
   */
  private Object getValueIgnoreCase(JSONObject row, String key) {
    Object value = row.get(key);
    if (value != null)
      return value;
    value = row.get(key.toLowerCase());
    if (value != null)
      return value;
    value = row.get(key.toUpperCase());
    return value;
  }
}
