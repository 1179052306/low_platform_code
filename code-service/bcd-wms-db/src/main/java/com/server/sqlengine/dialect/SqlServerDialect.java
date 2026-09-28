package com.server.sqlengine.dialect;

import com.server.db.DbType;
import org.springframework.stereotype.Component;

/**
 * SQL Server 方言实现。
 * <p>
 * SQL Server 2012+ 支持 {@code OFFSET m ROWS FETCH NEXT n ROWS ONLY}。
 * </p>
 *
 * @author liwei
 * @date 2026/9/9
 */
@Component
public class SqlServerDialect implements SqlDialect {

    @Override
    public String dbType() {
        return DbType.SQLSERVER;
    }

    @Override
    public String paginate(String sql, int limit, Integer offset) {
        int off = offset == null ? 0 : offset;
        return sql + " OFFSET " + off + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
    }

    @Override
    public boolean deleteNeedsFrom() {
        return false;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "[" + identifier + "]";
    }

    @Override
    public String booleanLiteral(boolean value) {
        return value ? "1" : "0";
    }

    // ==================== 时间/日期函数 ====================

    @Override
    public String currentTimestamp() {
        return "GETDATE()";
    }

    @Override
    public String sysDate(String format) {
        return "CONVERT(varchar, GETDATE(), " + format + ")";
    }

    @Override
    public String dateToTimestamp(String timeStr, String format) {
        return "CONVERT(datetime, '" + timeStr + "')";
    }

    @Override
    public String dateInterval(String value, String type) {
        return "DATEADD(" + type + ", " + value + ", base)";
    }

    @Override
    public String dateSubtraction(String startExpr, String endExpr) {
        return "DATEDIFF(day, " + endExpr + ", " + startExpr + ")";
    }

    // ==================== 序列函数 ====================

    @Override
    public String sequenceNextVal(String sequenceName) {
        return "NEXT VALUE FOR " + sequenceName;
    }

    @Override
    public String resetSequenceSql(String sequenceName, int startVal) {
        // SQL Server 不支持序列重置
        return null;
    }

    // ==================== 数学函数 ====================

    @Override
    public String ceil(String expr) {
        return "CEILING(" + expr + ")";
    }

    @Override
    public String floor(String expr) {
        return "FLOOR(" + expr + ")";
    }

    @Override
    public String mod(String expr, String divisor) {
        return expr + " % " + divisor;
    }

    @Override
    public String decimalPoint(String expr, int precision) {
        return "ROUND(" + expr + ", " + precision + ", 0)";
    }

    // ==================== 字符串函数 ====================

    @Override
    public String concatSymbol() {
        return "+";
    }

    @Override
    public String trim(String expr) {
        return "TRIM(" + expr + ")";
    }

    @Override
    public String convertVarchar(String expr) {
        return "CAST(" + expr + " AS varchar)";
    }

    // ==================== 行/伪列 ====================

    @Override
    public String rowLimit(int rows) {
        return "TOP " + rows;
    }

    @Override
    public String rowNumber() {
        return "ROW_NUMBER() OVER(ORDER BY (SELECT NULL))";
    }

    // ==================== 通用/语法 ====================

    @Override
    public String dummySelect(String expr) {
        return "SELECT " + expr;
    }

    @Override
    public String keywordCase(String sql) {
        return sql;
    }

    @Override
    public int batchResult(int[] results) {
        int count = 0;
        for (int r : results) {
            if (r == 1) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String createTableLike(String targetTable, String sourceTable) {
        return "SELECT * INTO " + targetTable + " FROM " + sourceTable + " WHERE 1=0";
    }

    // ==================== 元数据查询 ====================

    @Override
    public String queryTableColumnsSql() {
        return "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = ? "
                + "AND table_schema = (SELECT SCHEMA_NAME()) "
                + "ORDER BY ordinal_position";
    }

    @Override
    public String checkTableExistsSql() {
        return "SELECT 1 FROM information_schema.tables "
                + "WHERE table_name = ? "
                + "AND table_schema = (SELECT SCHEMA_NAME())";
    }

    @Override
    public String normalizeSchemaIdentifier(String identifier) {
        // SQL Server 默认排序规则通常不区分大小写，原样返回
        return identifier;
    }

    @Override
    public String queryTableSchemaSql() {
        return "SELECT column_name AS COLUMN_NAME, data_type AS DATA_TYPE, "
                + "character_maximum_length AS DATA_LENGTH, numeric_precision AS DATA_PRECISION, "
                + "numeric_scale AS DATA_SCALE, is_nullable AS IS_NULLABLE "
                + "FROM information_schema.columns "
                + "WHERE table_name = ? "
                + "AND table_schema = (SELECT SCHEMA_NAME()) "
                + "ORDER BY ordinal_position";
    }
}
