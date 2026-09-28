package com.server.sqlengine.dialect;

import com.server.db.DbType;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Oracle 方言实现。
 * <p>
 * Oracle 12c+ 支持 {@code OFFSET m ROWS FETCH NEXT n ROWS ONLY}；
 * 对于旧版兼容场景，使用 ROWNUM 子查询包装。
 * </p>
 *
 * <p>
 * Oracle 和达梦(DM)共享大部分语法，{@link DmDialect} 继承本类。
 * </p>
 *
 * @author liwei
 * @date 2026/9/9
 */
@Component
public class OracleDialect implements SqlDialect {

    @Override
    public String dbType() {
        return DbType.ORACLE;
    }

    @Override
    public String paginate(String sql, int limit, Integer offset) {
        int off = offset == null ? 0 : offset;
        if (off == 0) {
            return "SELECT * FROM (" + sql + ") WHERE ROWNUM <= " + limit;
        }
        return "SELECT * FROM (SELECT ROWNUM rn_, t_.* FROM (" + sql + ") t_ "
                + "WHERE ROWNUM <= " + (off + limit) + ") WHERE rn_ > " + off;
    }

    @Override
    public boolean deleteNeedsFrom() {
        return false;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }

    @Override
    public String booleanLiteral(boolean value) {
        return value ? "1" : "0";
    }

    // ==================== 时间/日期函数 ====================

    @Override
    public String currentTimestamp() {
        return "SYSTIMESTAMP";
    }

    @Override
    public String sysDate(String format) {
        return "to_char(SYSTIMESTAMP, '" + format + "')";
    }

    @Override
    public String dateToTimestamp(String timeStr, String format) {
        return "TO_TIMESTAMP('" + timeStr + "', '" + format + "')";
    }

    @Override
    public String dateInterval(String value, String type) {
        String t = type.toUpperCase();
        if ("YEAR".equals(t) || "MONTH".equals(t)) {
            return "NUMTOYMINTERVAL(" + value + ", '" + t + "')";
        }
        return "NUMTODSINTERVAL(" + value + ", '" + t + "')";
    }

    @Override
    public String dateSubtraction(String startExpr, String endExpr) {
        return "TRUNC(" + startExpr + " - " + endExpr + ")";
    }

    // ==================== 序列函数 ====================

    @Override
    public String sequenceNextVal(String sequenceName) {
        return sequenceName.toUpperCase() + ".NEXTVAL";
    }

    @Override
    public String resetSequenceSql(String sequenceName, int startVal) {
        return "SELECT SETVAL('" + sequenceName.toUpperCase() + "', " + startVal + ", 0) FROM DUAL";
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
        return "MOD(" + expr + ", " + divisor + ")";
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
        return "TO_CHAR(" + expr + ")";
    }

    // ==================== 行/伪列 ====================

    @Override
    public String rowLimit(int rows) {
        return "AND ROWNUM <= " + rows;
    }

    @Override
    public String rowNumber() {
        return "ROWNUM";
    }

    // ==================== 通用/语法 ====================

    @Override
    public String dummySelect(String expr) {
        return "SELECT " + expr + " FROM DUAL";
    }

    @Override
    public String keywordCase(String sql) {
        return sql.toUpperCase();
    }

    @Override
    public int batchResult(int[] results) {
        int count = 0;
        for (int r : results) {
            if (r == 1 || r == -2) {
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
        return "SELECT column_name FROM all_tab_columns "
                + "WHERE table_name = ? "
                + "AND owner = (SELECT user FROM dual) "
                + "ORDER BY column_id";
    }

    @Override
    public String checkTableExistsSql() {
        return "SELECT 1 FROM user_tables WHERE table_name = ?";
    }

    @Override
    public String normalizeSchemaIdentifier(String identifier) {
        // Oracle 标识符默认折叠为大写，user_tables/all_tab_columns 存大写
        return identifier.toUpperCase(Locale.ROOT);
    }

    @Override
    public String queryTableSchemaSql() {
        return "SELECT column_name AS COLUMN_NAME, data_type AS DATA_TYPE, "
                + "data_length AS DATA_LENGTH, data_precision AS DATA_PRECISION, "
                + "data_scale AS DATA_SCALE, nullable AS IS_NULLABLE "
                + "FROM all_tab_columns "
                + "WHERE table_name = ? "
                + "AND owner = (SELECT user FROM dual) "
                + "ORDER BY column_id";
    }
}
