package com.server.sqlengine.executor;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.db.DbConfig;
import com.server.sqlengine.model.CompiledSql;
import com.server.sqlengine.model.SqlParam;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

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
     * 获取底层连接 —— 直接从 HikariDataSource 拿，不经过旧 DbHelp。
     */
    private Connection getConnection(@Nullable String dbName) throws SQLException {
        HikariDataSource ds = dbConfig.getHikariDataSource(dbName);
        if (ds == null) {
            throw new SQLException("数据源未找到: dbName=" + dbName
                    + "，请检查 spring.datasource.names 配置");
        }
        return ds.getConnection();
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
            closeQuietly(conn);
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
            closeQuietly(conn);
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
            closeQuietly(conn);
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
        try {
            conn = getConnection(dbName);
            conn.setAutoCommit(false);
            ps = conn.prepareStatement(compiled.sql());

            for (List<SqlParam> rowParams : batchParams) {
                bindParams(ps, rowParams);
                ps.addBatch();
            }
            long start = System.currentTimeMillis();
            int[] result = ps.executeBatch();
            long duration = System.currentTimeMillis() - start;
            warnSlowSql(duration, compiled.sql());
            conn.commit();
            return result;
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw e;
        } catch (RuntimeException e) {
            rollbackQuietly(conn);
            throw e;
        } finally {
            closeQuietly(ps);
            closeQuietly(conn);
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
        try {
            conn = getConnection(dbName);
            conn.setAutoCommit(false);
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
            conn.commit();
            return totalRows;
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw e;
        } catch (RuntimeException e) {
            rollbackQuietly(conn);
            throw e;
        } finally {
            closeQuietly(ps);
            closeQuietly(conn);
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
            closeQuietly(conn);
        }
    }

    // ==================== 参数绑定 ====================

    private void bindParams(PreparedStatement ps, List<SqlParam> params) throws SQLException {
        if (params == null || params.isEmpty()) {
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i).value();
            if (value == null) {
                ps.setNull(i + 1, Types.NULL);
            } else {
                ps.setObject(i + 1, value);
            }
        }
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
     * 特殊列类型值转换 —— CLOB/NCLOB 转为 String，其余原样返回。
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
