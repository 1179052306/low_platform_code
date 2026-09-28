package com.server.sqlengine.dialect;

import com.server.db.DbType;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * PostgreSQL 方言实现。
 *
 * @author liwei
 * @date 2026/9/9
 */
@Component
public class PostgresSqlDialect implements SqlDialect {

    @Override
    public String dbType() {
        return DbType.POSTGRESQL;
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
        return "\"" + identifier + "\"";
    }

    @Override
    public String booleanLiteral(boolean value) {
        return value ? "TRUE" : "FALSE";
    }

    // ==================== 时间/日期函数 ====================

    @Override
    public String currentTimestamp() {
        return "CURRENT_TIMESTAMP";
    }

    @Override
    public String sysDate(String format) {
        return "to_char(current_timestamp, '" + format + "')";
    }

    @Override
    public String dateToTimestamp(String timeStr, String format) {
        return "to_timestamp('" + timeStr + "', '" + format + "')::timestamp";
    }

    @Override
    public String dateInterval(String value, String type) {
        return "INTERVAL '" + value + " " + type + "'";
    }

    @Override
    public String dateSubtraction(String startExpr, String endExpr) {
        return "FLOOR(EXTRACT(EPOCH FROM (" + startExpr + " - " + endExpr + ")) / 86400)";
    }

    // ==================== 序列函数 ====================

    @Override
    public String sequenceNextVal(String sequenceName) {
        return "nextval('" + sequenceName.toLowerCase() + "')";
    }

    @Override
    public String resetSequenceSql(String sequenceName, int startVal) {
        return "SELECT setval('" + sequenceName.toLowerCase() + "', " + startVal + ", false)";
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
        return expr + "::float";
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
        return "NVL(CAST(" + expr + " AS text), '')";
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
        return sql.toLowerCase();
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
        return "CREATE TABLE " + targetTable + " (LIKE " + sourceTable + " INCLUDING ALL)";
    }

    // ==================== 元数据查询 ====================

    @Override
    public String queryTableColumnsSql() {
        return "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = ? "
                + "AND table_schema = (SELECT current_schema()) "
                + "ORDER BY ordinal_position";
    }

    @Override
    public String checkTableExistsSql() {
        return "SELECT 1 FROM information_schema.tables "
                + "WHERE table_name = ? "
                + "AND table_schema = (SELECT current_schema())";
    }

    @Override
    public String normalizeSchemaIdentifier(String identifier) {
        // PostgreSQL 不带引号的标识符折叠为小写，information_schema 存小写
        return identifier.toLowerCase(Locale.ROOT);
    }

    @Override
    public String queryTableSchemaSql() {
        return "SELECT column_name AS COLUMN_NAME, data_type AS DATA_TYPE, "
                + "character_maximum_length AS DATA_LENGTH, numeric_precision AS DATA_PRECISION, "
                + "numeric_scale AS DATA_SCALE, is_nullable AS IS_NULLABLE "
                + "FROM information_schema.columns "
                + "WHERE table_name = ? "
                + "AND table_schema = (SELECT current_schema()) "
                + "ORDER BY ordinal_position";
    }
}
