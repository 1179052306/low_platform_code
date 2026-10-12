package com.server.sqlengine.dialect;

import com.server.db.DbType;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * SQLite 方言实现。
 *
 * @author liwei
 * @date 2026/9/9
 */
@Component
public class SqliteDialect implements SqlDialect {

    @Override
    public String dbType() {
        return DbType.SQLITE;
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
        return "CURRENT_TIMESTAMP";
    }

    @Override
    public String sysDate(String format) {
        return "strftime('" + format + "', 'now')";
    }

    @Override
    public String dateToTimestamp(String timeStr, String format) {
        return "datetime('" + timeStr + "')";
    }

    @Override
    public String dateInterval(String value, String type) {
        return "datetime(base, '+' || " + value + " || ' " + type + "')";
    }

    @Override
    public String dateSubtraction(String startExpr, String endExpr) {
        return "CAST(julianday(" + startExpr + ") - julianday(" + endExpr + ") AS INTEGER)";
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
        return "CAST(" + expr + " AS TEXT)";
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
        return "CREATE TABLE " + targetTable + " AS SELECT * FROM " + sourceTable + " WHERE 1=0";
    }

    // ==================== 元数据查询 ====================

    @Override
    public String queryTableColumnsSql() {
        return "SELECT name FROM pragma_table_info(?) ORDER BY cid";
    }

    @Override
    public String checkTableExistsSql() {
        return "SELECT 1 FROM sqlite_master WHERE type = 'table' AND LOWER(name) = LOWER(?)";
    }

    @Override
    public String queryTableSchemaSql() {
        return "SELECT name AS COLUMN_NAME, type AS DATA_TYPE, "
                + "NULL AS DATA_LENGTH, NULL AS DATA_PRECISION, NULL AS DATA_SCALE, "
                + "CASE WHEN \"notnull\" = 0 THEN 'YES' ELSE 'NO' END AS IS_NULLABLE "
                + "FROM pragma_table_info(?) ORDER BY cid";
    }
}