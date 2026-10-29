package com.server.sqlengine.executor;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.db.DbConfig;
import com.server.sqlengine.model.CompiledSql;
import com.server.sqlengine.model.SqlParam;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.Reader;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SQL 执行器 —— 全新引擎自有的 JDBC 执行层，不依赖旧 {@code DbHelp}。
 *
 * <p>
 * 完整覆盖旧 DbHelp 的批量操作场景：
 * </p>
 * <ul>
 * <li>{@link #query} — 单条参数化查询</li>
 * <li>{@link #batchQuery} — 批量查询：多条 SELECT 同一连接执行，返回多结果集</li>
 * <li>{@link #update} — 单条参数化增删改</li>
 * <li>{@link #batch} — 批量同构执行：同一 SQL 多组参数（批量新增/修改/删除）</li>
 * <li>{@link #executeInTransaction} — 混合事务：多条不同 SQL 原子提交</li>
 * <li>{@link #count} — COUNT 查询</li>
 * </ul>
 *
 * <p>
 * 所有 SQL 均为参数化执行（{@code ?} 占位符），杜绝注入。
 * 全方法内置慢 SQL 阈值监控，超时自动 {@code log.warn}。
 * </p>
 *
 * @author liwei
 * @date 2026/9/9
 */
@Slf4j
@Service
public class SqlExecutor {

    private final DbConfig dbConfig;
    private final SlowSqlThreshold slowSqlThreshold;

    @Autowired
    public SqlExecutor(DbConfig dbConfig) {
        this.dbConfig = dbConfig;
        this.slowSqlThreshold = new SlowSqlThreshold();
    }

    /**
     * 设置慢 SQL 阈值（毫秒），默认 3000ms。
     */
    public void setSlowSqlThreshold(long millis) {
        slowSqlThreshold.setThresholdMillis(millis);
    }

    /**
     * 获取底层连接 —— 走 {@link DataSourceUtils}：
     * 当前线程存在 Spring 事务绑定时复用绑定连接（事务真正生效），
     * 无绑定时行为与直取等价（自动提交，用完即还）。
     */
    private Connection getConnection(@Nullable String dbName) throws SQLException {
        HikariDataSource ds = dbConfig.getHikariDataSource(dbName);
        if (ds == null) {
            throw new SQLException("数据源未找到: dbName=" + dbName
                    + "，请检查 spring.datasource.names 配置");
        }
        return DataSourceUtils.getConnection(ds);
    }

    /**
     * 判断当前线程在该数据源上是否绑定了 Spring 事务资源。
     * 绑定时连接由外层事务管理（不能自行 commit/rollback）。
     */
    private boolean isBoundToSpringTx(@Nullable String dbName) {
        HikariDataSource ds = dbConfig.getHikariDataSource(dbName);
        return ds != null && TransactionSynchronizationManager.hasResource(ds);
    }

    /**
     * 归还连接 —— 绑定连接只减引用计数（由事务管理器最终归还），
     * 非绑定连接真正关闭（行为等同 close）。
     */
    private void releaseConnection(@Nullable Connection conn, @Nullable String dbName) {
        if (conn == null) {
            return;
        }
        HikariDataSource ds = dbConfig.getHikariDataSource(dbName);
        if (ds != null) {
            DataSourceUtils.releaseConnection(conn, ds);
        } else {
            closeQuietly(conn);
        }
    }

    // ==================== 单条查询 ====================

    /**
     * 参数化查询，返回 JSONArray（每行一个 JSONObject）。
     */
    public JSONArray query(@Nullable String dbName, CompiledSql compiled) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection(dbName);
            ps = conn.prepareStatement(compiled.sql());
            bindParams(ps, compiled.params());
            long start = System.currentTimeMillis();
            rs = ps.executeQuery();
            long duration = System.currentTimeMillis() - start;
            warnSlowSql(duration, compiled.sql());
            return resultSetToJsonArray(rs);
        } finally {
            closeQuietly(rs);
            closeQuietly(ps);
            releaseConnection(conn, dbName);
        }
    }

    // ==================== 批量查询 ====================

    /**
     * 批量查询 —— 多条 SELECT 在同一连接上执行，返回每个查询的结果集。
     *
     * <p>
     * 对应旧 {@code DbHelp.querykeep}，但不依赖外部传入 Connection。
     * 内部管理连接生命周期，全部查询完成后关闭。
     * </p>
     *
     * @param dbName  数据源名称
     * @param sqlList 多条编译后的 SELECT
     * @return 每条查询对应一个 JSONArray，列表大小 == sqlList.size()
     */
    public List<JSONArray> batchQuery(@Nullable String dbName, List<CompiledSql> sqlList) throws SQLException {
        List<JSONArray> results = new ArrayList<JSONArray>(sqlList.size());
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection(dbName);
            for (CompiledSql compiled : sqlList) {
                ps = conn.prepareStatement(compiled.sql());
                bindParams(ps, compiled.params());
                long start = System.currentTimeMillis();
                rs = ps.executeQuery();
                long duration = System.currentTimeMillis() - start;
                warnSlowSql(duration, compiled.sql());
                results.add(resultSetToJsonArray(rs));
                closeQuietly(rs);
                rs = null;
                closeQuietly(ps);
                ps = null;
            }
            return results;
        } finally {
            closeQuietly(rs);
            closeQuietly(ps);
            releaseConnection(conn, dbName);
        }
    }

    // ==================== 单条更新 ====================

    /**
     * 参数化更新（INSERT/UPDATE/DELETE），返回影响行数。
     */
    public int update(@Nullable String dbName, CompiledSql compiled) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getConnection(dbName);
            ps = conn.prepareStatement(compiled.sql());
            bindParams(ps, compiled.params());
            long start = System.currentTimeMillis();
            int rows = ps.executeUpdate();
            long duration = System.currentTimeMillis() - start;
            warnSlowSql(duration, compiled.sql());
            return rows;
        } finally {
            closeQuietly(ps);
            releaseConnection(conn, dbName);
        }
    }

    // ==================== 批量同构执行 ====================

    /**
     * 批量同构执行 —— 同一 SQL 多组参数（批量新增/批量修改/批量删除）。
     *
     * <p>
     * 对应旧 {@code DbHelp.executeSql} with {@code getListBatch()}。
     * 使用 {@code addBatch} + {@code executeBatch}，事务内原子提交。
     * </p>
     *
     * @param dbName      数据源名称
     * @param compiled    编译后的 SQL（只含 SQL 文本，参数从 batchParams 取）
     * @param batchParams 每组参数列表，每组对应一行
     * @return 每行影响行数数组
     */
    public int[] batch(@Nullable String dbName, CompiledSql compiled,
            List<List<SqlParam>> batchParams) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        boolean selfManagedTx = true;
        try {
            selfManagedTx = !isBoundToSpringTx(dbName);
            conn = getConnection(dbName);
            if (selfManagedTx) {
                conn.setAutoCommit(false);
            }
            ps = conn.prepareStatement(compiled.sql());

            for (List<SqlParam> rowParams : batchParams) {
                bindParams(ps, rowParams);
                ps.addBatch();
            }
            long start = System.currentTimeMillis();
            int[] result = ps.executeBatch();
            long duration = System.currentTimeMillis() - start;
            warnSlowSql(duration, compiled.sql());
            if (selfManagedTx) {
                conn.commit();
            }
            return result;
        } catch (SQLException e) {
            if (selfManagedTx) {
                rollbackQuietly(conn);
            }
            throw e;
        } catch (RuntimeException e) {
            if (selfManagedTx) {
                rollbackQuietly(conn);
            }
            throw e;
        } finally {
            closeQuietly(ps);
            releaseConnection(conn, dbName);
        }
    }

    // ==================== 混合事务 ====================

    /**
     * 混合事务执行 —— 多条不同 SQL（增删改混合）在同一事务中执行，全成功才提交。
     *
     * <p>
     * 对应旧 {@code DbHelp.executeSqlTran(List)}。
     * </p>
     *
     * @param dbName  数据源名称
     * @param sqlList 多条编译后的 SQL（INSERT/UPDATE/DELETE 混合）
     * @return 总影响行数
     */
    public int executeInTransaction(@Nullable String dbName, List<CompiledSql> sqlList) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        int totalRows = 0;
        boolean selfManagedTx = true;
        try {
            selfManagedTx = !isBoundToSpringTx(dbName);
            conn = getConnection(dbName);
            if (selfManagedTx) {
                conn.setAutoCommit(false);
            }
            for (CompiledSql compiled : sqlList) {
                ps = conn.prepareStatement(compiled.sql());
                bindParams(ps, compiled.params());
                long start = System.currentTimeMillis();
                int rows = ps.executeUpdate();
                long duration = System.currentTimeMillis() - start;
                warnSlowSql(duration, compiled.sql());
                totalRows += rows;
                closeQuietly(ps);
                ps = null;
            }
            if (selfManagedTx) {
                conn.commit();
            }
            return totalRows;
        } catch (SQLException e) {
            if (selfManagedTx) {
                rollbackQuietly(conn);
            }
            throw e;
        } catch (RuntimeException e) {
            if (selfManagedTx) {
                rollbackQuietly(conn);
            }
            throw e;
        } finally {
            closeQuietly(ps);
            releaseConnection(conn, dbName);
        }
    }

    // ==================== COUNT 查询 ====================

    /**
     * 查询行数（SELECT COUNT(*) 场景）。
     */
    public long count(@Nullable String dbName, CompiledSql compiled) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection(dbName);
            ps = conn.prepareStatement(compiled.sql());
            bindParams(ps, compiled.params());
            long start = System.currentTimeMillis();
            rs = ps.executeQuery();
            long duration = System.currentTimeMillis() - start;
            warnSlowSql(duration, compiled.sql());
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0L;
        } finally {
            closeQuietly(rs);
            closeQuietly(ps);
            releaseConnection(conn, dbName);
        }
    }

    // ==================== 参数绑定 ====================

    private void bindParams(PreparedStatement ps, List<SqlParam> params) throws SQLException {
        if (params == null || params.isEmpty()) {
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            SqlParam param = params.get(i);
            Object value = param.value();
            if (value == null) {
                ps.setNull(i + 1, Types.NULL);
            } else {
                Object converted = convertValueByTypeHint(value, param.typeHint());
                ps.setObject(i + 1, converted);
            }
        }
    }

    /** 根据列类型提示转换值：以列类型为驱动双向转换
     *  <p>正向：String → Long/Double/Boolean/Timestamp/Date（数值/日期/布尔列收到字符串）
     *  <br>反向：Number → String（varchar/char/text 列收到数字）——PostgreSQL 不支持 varchar = integer 隐式转换
     */
    private Object convertValueByTypeHint(Object value, String typeHint) {
        if (value == null || typeHint == null) {
            return value;
        }
        String t = typeHint.toLowerCase(Locale.ROOT);
        if ((t.contains("varchar") || t.contains("char") || t.contains("text"))
                && value instanceof Number) {
            return String.valueOf(value);
        }
        if (!(value instanceof String)) {
            return value;
        }
        String strValue = (String) value;
        if (strValue.isEmpty()) {
            return value;
        }
        if (t.contains("bigint") || t.contains("int") || t.contains("numeric") || t.contains("decimal")) {
            try {
                return Long.parseLong(strValue);
            } catch (NumberFormatException e1) {
                try {
                    return Double.parseDouble(strValue);
                } catch (NumberFormatException e2) {
                    return value;
                }
            }
        }
        if (t.contains("timestamp") || t.contains("datetime")) {
            try {
                return java.sql.Timestamp.valueOf(strValue);
            } catch (IllegalArgumentException e) {
                try {
                    return java.sql.Timestamp.valueOf(strValue + " 00:00:00");
                } catch (IllegalArgumentException e2) {
                    return value;
                }
            }
        }
        if (t.contains("date") && !t.contains("datetime")) {
            try {
                return java.sql.Date.valueOf(strValue);
            } catch (IllegalArgumentException e) {
                return value;
            }
        }
        if (t.contains("boolean") || t.contains("bool")) {
            if ("true".equalsIgnoreCase(strValue) || "1".equals(strValue) || "t".equals(strValue)) return Boolean.TRUE;
            if ("false".equalsIgnoreCase(strValue) || "0".equals(strValue) || "f".equals(strValue)) return Boolean.FALSE;
            return value;
        }
        return value;
    }

    // ==================== 慢 SQL 监控 ====================

    private void warnSlowSql(long durationMillis, String sql) {
        if (durationMillis > slowSqlThreshold.getThresholdMillis()) {
            log.warn("【慢SQL警告】执行耗时: {} ms, SQL: {}", durationMillis, sql);
        }
    }

    // ==================== ResultSet → JSON ====================

    /**
     * ResultSet 转 JSONArray —— 合并旧 DbHelp 的性能优化和新实现的语义正确性。
     *
     * <p>
     * 优化点（取自旧实现）：
     * </p>
     * <ul>
     * <li>循环外预先提取列名和列类型数组，避免逐行重复调用 meta.getXxx()</li>
     * <li>预分配 JSONObject 容量，避免哈希表扩容</li>
     * <li>CLOB / NCLOB 转为 String，避免 Oracle 大字段序列化失败</li>
     * </ul>
     *
     * <p>
     * 改进点（取自新实现）：
     * </p>
     * <ul>
     * <li>NULL 值保留为 null 而非空字符串，语义更准确</li>
     * <li>列名统一转大写，兼容 getColumnName fallback</li>
     * <li>异常类型精确为 SQLException</li>
     * </ul>
     */
    private JSONArray resultSetToJsonArray(ResultSet rs) throws SQLException {
        JSONArray result = new JSONArray();
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        // 预缓存列名和列类型 —— N 行 M 列只调 M 次 meta，而非 N×M 次
        String[] columnLabels = new String[columnCount];
        for (int i = 0; i < columnCount; i++) {
            String label = meta.getColumnLabel(i + 1);
            if (label == null || label.isEmpty()) {
                label = meta.getColumnName(i + 1);
            }
            columnLabels[i] = label.toUpperCase(Locale.ROOT);
        }

        while (rs.next()) {
            JSONObject row = new JSONObject(columnCount);
            for (int i = 0; i < columnCount; i++) {
                Object value = rs.getObject(i + 1);
                row.put(columnLabels[i], convertValue(value));
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 特殊列类型值转换 —— CLOB/NCLOB 转为 String；
     * Long/BigInteger 超过 JS 安全整数范围（2^53-1）转为 String，避免前端精度丢失。
     */
    private Object convertValue(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof NClob) {
            return clobToString((NClob) value);
        }
        if (value instanceof Clob) {
            return clobToString((Clob) value);
        }
        if (value instanceof Long) {
            long lv = (Long) value;
            if (lv > 9007199254740991L || lv < -9007199254740991L) {
                return String.valueOf(lv);
            }
            return value;
        }
        if (value instanceof java.math.BigInteger) {
            java.math.BigInteger bi = (java.math.BigInteger) value;
            java.math.BigInteger limit = java.math.BigInteger.valueOf(9007199254740991L);
            if (bi.abs().compareTo(limit) > 0) {
                return bi.toString();
            }
            return value;
        }
        return value;
    }

    private String clobToString(Clob clob) throws SQLException {
        if (clob == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try (Reader reader = clob.getCharacterStream()) {
            char[] buffer = new char[4096];
            int charsRead;
            while ((charsRead = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, charsRead);
            }
        } catch (IOException e) {
            log.warn("CLOB 读取失败", e);
            return null;
        }
        return sb.toString();
    }

    // ==================== 资源管理 ====================

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.debug("关闭 JDBC 资源失败", e);
            }
        }
    }

    private void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (Exception e) {
                log.error("事务回滚失败", e);
            }
        }
    }
}
