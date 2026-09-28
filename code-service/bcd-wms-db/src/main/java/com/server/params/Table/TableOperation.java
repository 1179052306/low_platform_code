package com.server.params.Table;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.db.DbConfig;
import com.server.sqlengine.dialect.SqlDialect;
import com.server.sqlengine.dialect.SqlDialectRegistry;
import com.server.sqlengine.executor.SqlExecutor;
import com.server.sqlengine.model.CompiledSql;
import com.server.sqlengine.model.SqlParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表结构元数据缓存服务（支持多数据库）。
 * <p>
 * 通过 {@link SqlDialect#queryTableSchemaSql()} 获取各方言的表字段元数据查询 SQL，
 * 复用 {@link SqlExecutor#query} 执行，不直接操作 JDBC，不硬编码数据库特有语法。
 * 查询结果统一返回 COLUMN_NAME/DATA_TYPE/DATA_LENGTH 等列，按字段名构造 {@link Table} 缓存。
 *
 * @author lw
 * @date: 2026/4/10
 */
@Service
public class TableOperation {

    /** SQL 执行器：CompiledSql → JSONArray */
    @Autowired
    private SqlExecutor sqlExecutor;

    /** SQL 方言注册表：按数据源名获取对应方言 */
    @Autowired
    private SqlDialectRegistry dialectRegistry;

    @Resource
    DbConfig dbConfig;

    /**
     * 缓存结构：dbName -> (tableName -> Table)
     * 外层和内层均为 ConcurrentHashMap，确保高并发读安全
     */
    private final Map<String, Map<String, Table>> columnMetadataCache = new ConcurrentHashMap<>();

    /**
     * 获取指定数据库和表的元数据（带缓存）。
     *
     * @param dbName    数据源名
     * @param tableName 表名
     * @return 表结构元数据
     */
    public Table getTable(String dbName, String tableName) {

        String upperTableName = tableName.toUpperCase(Locale.ROOT);

        // 原子性获取或创建 dbName 对应的表缓存
        Map<String, Table> tableMap = columnMetadataCache
                .computeIfAbsent(dbName, k -> new ConcurrentHashMap<>());

        // 原子性加载 Table（避免多线程重复解析）
        return tableMap.computeIfAbsent(upperTableName, k -> {
            try {
                return loadTable(dbName, tableName);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 获取指定字段的元数据信息。
     * <p>
     * 若缓存中找不到字段，直接从数据库重新加载（数据库是权威数据源），
     * 刷新后仍找不到则抛异常。
     *
     * @param dbName     数据源名
     * @param tableName  表名（可含数据源前缀，如 "dbSource.tableName"）
     * @param columnName 列名
     * @return 字段元数据节点
     * @throws Exception 表或字段不存在时抛出
     */
    public TableNode getTableNode(String dbName, String tableName, String columnName) throws Exception {
        if (tableName.contains(".")) {
            String dbSourceName = tableName.split("\\.")[0];
            dbName = dbConfig.getDbName(dbSourceName);
            if (dbName == null) {
                throw new Exception("表 [" + tableName + "] 的未找到合适的数据源进行查询");
            }
        }
        String upperTableName = tableName.toUpperCase(Locale.ROOT).trim();

        String upperColumnName = columnName.toUpperCase(Locale.ROOT).trim();

        Table table = getTable(dbName, upperTableName);
        TableNode node = table.getColumn(upperColumnName);

        if (node == null) {
            // 缓存未命中，可能是缓存过期，强制刷新从数据库重新加载
            Map<String, Table> tableMap = columnMetadataCache.get(dbName);
            if (tableMap != null) {
                tableMap.remove(upperTableName);
            }
            table = getTable(dbName, upperTableName);
            node = table.getColumn(upperColumnName);
            if (node == null) {
                throw new Exception("参数名 [" + columnName + "] 在表 [" + tableName + "] 中不存在，请检查配置");
            }
        }
        return node;
    }

    /**
     * 重置指定表的缓存并重新加载。
     *
     * @param dbName     数据源名
     * @param tableName  表名
     * @param columnName 列名（未使用，保留兼容签名）
     * @throws Exception 加载异常
     */
    public void resetTable(String dbName, String tableName, String columnName) throws Exception {
        String upperTableName = tableName.toUpperCase(Locale.ROOT).trim();
        Map<String, Table> tableMap = columnMetadataCache.get(dbName);
        if (tableMap != null) {
            tableMap.remove(upperTableName); // 移除旧缓存
        }
        getTable(dbName, upperTableName);
    }

    /**
     * 通过 sqlengine 从数据库加载表字段元数据（仅内部调用）。
     * <p>
     * 使用 {@link SqlDialect#queryTableSchemaSql()} 获取各方言的元数据查询 SQL，
     * 返回结果包含 COLUMN_NAME/DATA_TYPE/DATA_LENGTH 等列，按字段名构造 {@link Table}。
     *
     * @param dbName    数据源名
     * @param tableName 表名
     * @return 表结构元数据
     */
    private Table loadTable(String dbName, String tableName) throws SQLException {
        // 通过方言获取表结构查询 SQL（含 COLUMN_NAME, DATA_TYPE, DATA_LENGTH 等列）
        SqlDialect dialect = dialectRegistry.dialect(dbName);
        String sql = dialect.queryTableSchemaSql();
        // 表名按方言规则转换大小写（PG 存小写、Oracle 存大写）
        String schemaTableName = dialect.normalizeSchemaIdentifier(tableName);

        List<SqlParam> params = new ArrayList<SqlParam>();
        params.add(new SqlParam("table_name", schemaTableName));
        CompiledSql compiled = new CompiledSql(sql, params);

        JSONArray data = sqlExecutor.query(dbName, compiled);
        if (data.isEmpty()) {
            throw new IllegalStateException("无法加载表 [" + tableName + "] 在数据库 [" + dbName + "] 中的配置");
        }

        Map<String, TableNode> columnMap = new HashMap<String, TableNode>();
        for (int i = 0; i < data.size(); i++) {
            JSONObject row = data.getJSONObject(i);
            // SqlExecutor 返回的 JSON key 已统一转大写
            String id = row.getString("COLUMN_NAME").toUpperCase(Locale.ROOT);
            String oracleType = row.getString("DATA_TYPE");
            String oracleSize = row.getString("DATA_LENGTH");
            columnMap.put(id, new TableNode(oracleType, oracleSize));
        }

        return new Table(columnMap);
    }

    /**
     * （可选）清除指定数据库的缓存。
     *
     * @param dbName 数据源名
     */
    public void clearCacheByDb(String dbName) {
        String key = dbName.toUpperCase(Locale.ROOT);
        columnMetadataCache.remove(key);
    }

    /**
     * （可选）清除所有缓存（用于系统重载）。
     * 
     */
    public void clearAllCache() {
        columnMetadataCache.clear();
    }
}
