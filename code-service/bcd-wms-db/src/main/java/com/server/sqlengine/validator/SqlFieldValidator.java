package com.server.sqlengine.validator;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.dialect.SqlDialect;
import com.server.sqlengine.dialect.SqlDialectRegistry;
import com.server.sqlengine.executor.SqlExecutor;
import com.server.sqlengine.model.CompiledSql;
import com.server.sqlengine.model.SqlParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * SQL 字段合法性校验器 —— 白名单 + 元数据双重校验。
 *
 * <p>
 * 三层防御：
 * </p>
 * <ol>
 * <li><b>标识符格式校验</b> — 表名/列名只允许 {@code [a-zA-Z][a-zA-Z0-9_]*}，
 * 从源头杜绝 SQL 注入（分号、注释符、UNION 等无法通过正则）</li>
 * <li><b>表存在性校验</b> — 通过 {@link SqlDialect#checkTableExistsSql()}
 * 查系统表确认表存在</li>
 * <li><b>列存在性校验</b> — 通过 {@link SqlDialect#queryTableColumnsSql()}
 * 查系统表确认列属于该表</li>
 * </ol>
 *
 * <p>
 * 元数据查询结果按 {@code dbName:tableName} 缓存，TTL 5 分钟，
 * 避免每次请求都查系统表。
 * </p>
 *
 * <p>
 * 相对旧 {@code SQLSanitizer} 的改进：旧实现是黑名单关键字过滤（可被绕过），
 * 新实现是白名单正则 + 数据库元数据校验，安全等级根本不同。
 * </p>
 *
 * @author liwei
 * @date 2026/9/9
 */
@Slf4j
@Service
public class SqlFieldValidator {

    private final SqlExecutor sqlExecutor;
    private final SqlDialectRegistry dialectRegistry;

    /** 标识符白名单：字母开头，只含字母、数字、下划线，长度 1-63（PG 限制） */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,62}$");

    /** 元数据缓存：dbName:tableName → 列名集合（大写）。TTL 5 分钟 */
    private final Map<String, TableMeta> metaCache = new ConcurrentHashMap<String, TableMeta>();

    /** 缓存 TTL（毫秒） */
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    @Autowired
    public SqlFieldValidator(SqlExecutor sqlExecutor, SqlDialectRegistry dialectRegistry) {
        this.sqlExecutor = sqlExecutor;
        this.dialectRegistry = dialectRegistry;
    }

    /**
     * 表元数据缓存项。
     */
    private static class TableMeta {
        final Set<String> columnsUpper;
        final long createdAt;
        final boolean tableExists;

        TableMeta(Set<String> columnsUpper, boolean tableExists) {
            this.columnsUpper = columnsUpper;
            this.tableExists = tableExists;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
        }
    }

    // ==================== 标识符格式校验 ====================

    /**
     * 校验标识符（表名/列名）格式合法性。
     *
     * @param identifier 标识符
     * @param label      字段标签（用于错误信息，如 "tableName" / "columnName"）
     * @throws IllegalArgumentException 格式不合法
     */
    public void validateIdentifier(String identifier, String label) {
        if (identifier == null || identifier.isEmpty()) {
            throw new IllegalArgumentException(label + " 不能为空");
        }
        if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                    label + " 格式不合法: [" + identifier
                            + "]，只允许字母开头、字母+数字+下划线组合");
        }
        // 防御 SQL 关键字作为表名/列名（如 SELECT、FROM、WHERE）
        String upper = identifier.toUpperCase(Locale.ROOT);
        if (SQL_KEYWORDS.contains(upper)) {
            throw new IllegalArgumentException(
                    label + " 不能使用 SQL 保留字: " + identifier);
        }
    }

    /**
     * 批量校验列名格式。
     *
     * @param columns 列名集合
     * @param label   字段标签
     */
    public void validateColumns(Collection<String> columns, String label) {
        if (columns == null || columns.isEmpty()) {
            return;
        }
        for (String col : columns) {
            validateIdentifier(col, label);
        }
    }

    // ==================== 表存在性 + 列存在性校验 ====================

    /**
     * 校验表是否存在 + 列是否属于该表。
     *
     * <p>
     * 如果 {@code columns} 为空，只校验表存在性。
     * 元数据查询失败（如权限不足）时降级为仅格式校验，不阻断流程。
     * </p>
     *
     * @param dbName    数据源名
     * @param tableName 表名（已通过格式校验）
     * @param columns   需要校验的列名集合（可为 null 或空）
     * @throws IllegalArgumentException 表不存在或列不属于该表
     */
    public void validateTableAndColumns(@Nullable String dbName, String tableName,
            @Nullable Collection<String> columns) {
        // 先做格式校验
        validateIdentifier(tableName, "tableName");
        if (columns != null) {
            validateColumns(columns, "columnName");
        }

        // 元数据校验
        TableMeta meta = getOrLoadMeta(dbName, tableName);
        if (!meta.tableExists) {
            throw new IllegalArgumentException("表不存在: " + tableName
                    + "（数据源: " + (dbName != null ? dbName : "default") + "）");
        }

        if (columns != null && !columns.isEmpty()) {
            for (String col : columns) {
                String upperCol = col.toUpperCase(Locale.ROOT);
                if (!meta.columnsUpper.contains(upperCol)) {
                    throw new IllegalArgumentException(
                            "列 [" + col + "] 不属于表 [" + tableName + "]，"
                                    + "该表的列为: " + meta.columnsUpper);
                }
            }
        }
    }

    /**
     * 校验 SET 子句的列 + 值（INSERT/UPDATE 场景）。
     *
     * @param dbName    数据源名
     * @param tableName 表名
     * @param data      列名 → 值
     */
    public void validateSetClauses(@Nullable String dbName, String tableName, Map<String, ?> data) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("data 不能为空");
        }
        validateTableAndColumns(dbName, tableName, data.keySet());
    }

    /**
     * 校验 WHERE 条件中的列名。
     *
     * @param dbName           数据源名
     * @param tableName        表名
     * @param conditionColumns WHERE 条件引用的列名集合
     */
    public void validateConditionColumns(@Nullable String dbName, String tableName,
            Collection<String> conditionColumns) {
        validateTableAndColumns(dbName, tableName, conditionColumns);
    }

    // ==================== 元数据加载 ====================

    /**
     * 获取或加载表元数据（带缓存）。
     */
    private TableMeta getOrLoadMeta(@Nullable String dbName, String tableName) {
        String cacheKey = (dbName != null ? dbName : "default") + ":" + tableName.toUpperCase(Locale.ROOT);

        TableMeta cached = metaCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }

        TableMeta meta = loadTableMeta(dbName, tableName);
        metaCache.put(cacheKey, meta);
        return meta;
    }

    /**
     * 查询表的所有列名。
     *
     * <p>
     * 通过 {@link SqlDialect#queryTableColumnsSql()} 获取各方言的元数据查询 SQL，
     * 复用 {@link SqlExecutor#query} 执行，不直接操作 JDBC，不硬编码数据库特有语法。
     * </p>
     */
    private TableMeta loadTableMeta(@Nullable String dbName, String tableName) {
        try {
            // 通过方言获取元数据查询 SQL，不硬编码 PG/Oracle 特有语法
            SqlDialect dialect = dialectRegistry.dialect(dbName);
            String sql = dialect.queryTableColumnsSql();
            // 表名按方言规则转换大小写（PG 存小写、Oracle 存大写）
            String schemaTableName = dialect.normalizeSchemaIdentifier(tableName);

            List<SqlParam> params = new ArrayList<SqlParam>();
            params.add(new SqlParam("table_name", schemaTableName));
            CompiledSql compiled = new CompiledSql(sql, params);

            JSONArray rows = sqlExecutor.query(dbName, compiled);

            Set<String> columns = new LinkedHashSet<String>();
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                // SqlExecutor 返回的 JSON key 已统一转大写，故用大写 COLUMN_NAME 取值
                String col = row.getString("COLUMN_NAME");
                if (col != null) {
                    columns.add(col.toUpperCase(Locale.ROOT));
                }
            }

            if (columns.isEmpty()) {
                // 可能是表不存在，也可能是元数据表权限不足
                // 通过方言查系统表确认表是否存在
                return checkTableExists(dbName, tableName);
            }

            return new TableMeta(columns, true);

        } catch (SQLException e) {
            log.warn("元数据查询失败，降级为仅格式校验: table={}, error={}", tableName, e.getMessage());
            // 降级：不阻断，但标记表存在（信任格式校验已通过）
            return new TableMeta(Collections.<String>emptySet(), true);
        } catch (Exception e) {
            log.warn("元数据校验异常，降级为仅格式校验: table={}, error={}", tableName, e.getMessage());
            return new TableMeta(Collections.<String>emptySet(), true);
        }
    }

    /**
     * 兜底：通过方言查系统表确认表是否存在。
     *
     * <p>
     * 通过 {@link SqlDialect#checkTableExistsSql()} 获取各方言的表存在性检查 SQL，
     * 参数化查询，不拼接表名到 SQL，杜绝注入风险。
     * </p>
     */
    private TableMeta checkTableExists(@Nullable String dbName, String tableName) {
        try {
            SqlDialect dialect = dialectRegistry.dialect(dbName);
            String sql = dialect.checkTableExistsSql();
            String schemaTableName = dialect.normalizeSchemaIdentifier(tableName);

            List<SqlParam> params = new ArrayList<SqlParam>();
            params.add(new SqlParam("table_name", schemaTableName));
            CompiledSql compiled = new CompiledSql(sql, params);

            JSONArray rows = sqlExecutor.query(dbName, compiled);
            if (rows.isEmpty()) {
                // 系统表中查不到，表确实不存在
                return new TableMeta(Collections.<String>emptySet(), false);
            }
            // 表存在但元数据查不到列（权限不足）
            return new TableMeta(Collections.<String>emptySet(), true);
        } catch (SQLException e) {
            // 查询异常，降级为不存在
            return new TableMeta(Collections.<String>emptySet(), false);
        }
    }

    // ==================== 清除缓存 ====================

    /**
     * 清除指定表的元数据缓存（DDL 变更后调用）。
     */
    public void evictCache(@Nullable String dbName, String tableName) {
        String cacheKey = (dbName != null ? dbName : "default") + ":" + tableName.toUpperCase(Locale.ROOT);
        metaCache.remove(cacheKey);
    }

    /**
     * 清除全部缓存。
     */
    public void evictAllCache() {
        metaCache.clear();
    }

    // ==================== 工具方法 ====================

    /** SQL 保留字集合（不可用作表名/列名） */
    private static final Set<String> SQL_KEYWORDS = new HashSet<String>(Arrays.asList(
            "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP",
            "ALTER", "TABLE", "INDEX", "VIEW", "JOIN", "INNER", "LEFT", "RIGHT", "OUTER",
            "ON", "AND", "OR", "NOT", "NULL", "IS", "IN", "BETWEEN", "LIKE", "EXISTS",
            "GROUP", "BY", "ORDER", "HAVING", "UNION", "ALL", "DISTINCT", "AS", "CASE",
            "WHEN", "THEN", "ELSE", "END", "CAST", "SET", "VALUES", "INTO", "LIMIT",
            "OFFSET", "FETCH", "FIRST", "NEXT", "ROWS", "ONLY", "USING", "WITH",
            "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CONSTRAINT", "DEFAULT",
            "UNIQUE", "CHECK", "TRIGGER", "PROCEDURE", "FUNCTION", "EXEC", "CALL",
            "GRANT", "REVOKE", "COMMIT", "ROLLBACK", "SAVEPOINT", "TRANSACTION",
            "TRUNCATE", "MERGE", "EXPLAIN", "DESCRIBE", "DESC", "ASC", "TRUE", "FALSE"));
}
