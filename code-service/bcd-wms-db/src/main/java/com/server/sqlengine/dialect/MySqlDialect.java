package com.server.sqlengine.dialect;

import com.server.db.DbType;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * MySQL 方言实现。
 *
 * @author liwei
 * @date 2026/9/9
 */
@Component
public class MySqlDialect implements SqlDialect {

    @Override
    public String dbType() {
        return DbType.MYSQL;
    }

    @Override
    public String paginate(String sql, int limit, Integer offset) {
        StringBuilder sb = new StringBuilder(sql);
        sb.append(" LIMIT ").append(limit);
        if (offset != null && offset > 0) {
            sb.append(" OFFSET ").append(offset);
        }
        return sb.toString();
    }

    @Override
    public boolean deleteNeedsFrom() {
        return true;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return identifier.toLowerCase(Locale.ROOT);
    }

    @Override
    public String formatColumnRef(String column) {
        return column.toLowerCase(Locale.ROOT);
    }

    @Override
    public String booleanLiteral(boolean value) {
        return value ? "1" : "0";
    }

    // ==================== 时间/日期函数 ====================

    @Override
    public String currentTimestamp() {
        return "NOW()";
    }

    @Override
    public String sysDate(String format) {
        return "DATE_FORMAT(NOW(), '" + format + "')";
    }

    @Override
    public String dateToTimestamp(String timeStr, String format) {
        return "STR_TO_DATE('" + timeStr + "', '" + format + "')";
    }

    @Override
    public String dateInterval(String value, String type) {
        return "INTERVAL " + value + " " + type;
    }

    @Override
    public String dateSubtraction(String startExpr, String endExpr) {
        return "DATEDIFF(" + startExpr + ", " + endExpr + ")";
    }

    // ==================== 序列函数 ====================

    @Override
    public String sequenceNextVal(String sequenceName) {
        return null;
    }

    @Override
    public String resetSequenceSql(String sequenceName, int startVal) {
        return null;
    }

    // ==================== 数学函数 ====================

    @Override
    public String ceil(String expr) {
        return "CEIL(" + expr + ")";
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
        return "ROUND(" + expr + ", " + precision + ")";
    }

    // ==================== 字符串函数 ====================

    @Override
    public String concatSymbol() {
        return "||";
    }

    @Override
    public String trim(String expr) {
        return "TRIM(" + expr + ")";
    }

    @Override
    public String convertVarchar(String expr) {
        return "CAST(" + expr + " AS char)";
    }

    // ==================== 行/伪列 ====================

    @Override
    public String rowLimit(int rows) {
        return "LIMIT " + rows;
    }

    @Override
    public String rowNumber() {
        return "row_number() over()";
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
        return "CREATE TABLE " + targetTable + " LIKE " + sourceTable;
    }

    // ==================== 元数据查询 ====================

    @Override
    public String queryTableColumnsSql() {
        return "SELECT column_name FROM information_schema.columns "
                + "WHERE LOWER(table_name) = LOWER(?) "
                + "AND table_schema = (SELECT DATABASE()) "
                + "ORDER BY ordinal_position";
    }

    @Override
    public String checkTableExistsSql() {
        return "SELECT 1 FROM information_schema.tables "
                + "WHERE LOWER(table_name) = LOWER(?) "
                + "AND table_schema = (SELECT DATABASE())";
    }

    @Override
    public String queryTableSchemaSql() {
        return "SELECT column_name AS COLUMN_NAME, data_type AS DATA_TYPE, "
                + "character_maximum_length AS DATA_LENGTH, numeric_precision AS DATA_PRECISION, "
                + "numeric_scale AS DATA_SCALE, is_nullable AS IS_NULLABLE "
                + "FROM information_schema.columns "
                + "WHERE LOWER(table_name) = LOWER(?) "
                + "AND table_schema = (SELECT DATABASE()) "
                + "ORDER BY ordinal_position";
    }
}