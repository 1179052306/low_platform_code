package com.server.sqlengine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * INSERT 语句模型 —— 不可变。
 *
 * <p>支持三种列模式：</p>
 * <ol>
 *   <li><b>参数绑定列</b> — {@code set(col, value)}，编译为 {@code col = ?}（最常用）</li>
 *   <li><b>原始表达式列</b> — {@code setExpr(col, "CURRENT_TIMESTAMP")}，
 *       编译为 {@code col = CURRENT_TIMESTAMP}（对应旧 {@code Rpt}，不参数化直接拼入 SQL）</li>
 *   <li><b>跳过列</b> — {@code skip(col)}，INSERT 中完全不出现该列，
 *       由数据库默认值/触发器/序列填充（对应旧 {@code SkipColumn}）</li>
 * </ol>
 *
 * <p>批量插入时，跳过列和原始表达式列对每行都生效，只有参数绑定列的值逐行不同。</p>
 *
 * @author liwei
 * @date 2026/9/9
 */
public final class InsertStatement {

    private final String table;
    private final Map<String, Object> values;
    private final Map<String, String> rawExpressions;
    private final Set<String> skipColumns;
    private final List<Map<String, Object>> batchRows;

    private InsertStatement(Builder b) {
        this.table = b.table;
        this.rawExpressions = Collections.unmodifiableMap(
                new LinkedHashMap<String, String>(b.rawExpressions));
        this.skipColumns = Collections.unmodifiableSet(
                new LinkedHashSet<String>(b.skipColumns));
        if (b.batchMode) {
            this.values = Collections.emptyMap();
            this.batchRows = Collections.unmodifiableList(
                    new ArrayList<Map<String, Object>>(b.batchRows));
        } else {
            this.values = Collections.unmodifiableMap(
                    new LinkedHashMap<String, Object>(b.values));
            this.batchRows = Collections.emptyList();
        }
    }

    public String table() {
        return table;
    }

    public Map<String, Object> values() {
        return values;
    }

    /**
     * 原始 SQL 表达式列（列名 → SQL 字面量，如 CURRENT_TIMESTAMP、SYSDATE）。
     */
    public Map<String, String> rawExpressions() {
        return rawExpressions;
    }

    /**
     * 跳过列集合（INSERT 中不出现的列，由数据库默认值填充）。
     */
    public Set<String> skipColumns() {
        return skipColumns;
    }

    public List<Map<String, Object>> batchRows() {
        return batchRows;
    }

    public boolean isBatch() {
        return !batchRows.isEmpty();
    }

    /**
     * 最终参与 INSERT 的列名列表（参数绑定列 + 原始表达式列，排除跳过列）。
     */
    public List<String> columns() {
        if (isBatch()) {
            return new ArrayList<String>(batchRows.get(0).keySet());
        }
        List<String> cols = new ArrayList<String>(values.keySet());
        cols.addAll(rawExpressions.keySet());
        return cols;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String table;
        private final LinkedHashMap<String, Object> values = new LinkedHashMap<String, Object>();
        private final LinkedHashMap<String, String> rawExpressions = new LinkedHashMap<String, String>();
        private final LinkedHashSet<String> skipColumns = new LinkedHashSet<String>();
        private boolean batchMode = false;
        private final List<Map<String, Object>> batchRows = new ArrayList<Map<String, Object>>();

        public Builder into(String table) {
            this.table = table;
            return this;
        }

        /**
         * 参数绑定列：编译为 {@code col = ?}。
         */
        public Builder set(String column, Object value) {
            values.put(column, value);
            return this;
        }

        public Builder setAll(Map<String, Object> map) {
            values.putAll(map);
            return this;
        }

        /**
         * 原始 SQL 表达式列：编译为 {@code col = <rawSql>}，不参数化。
         * <p>用于数据库时间（CURRENT_TIMESTAMP）、主键序列（NEXTVAL）等场景。</p>
         *
         * @param column 列名
         * @param rawSql SQL 原始表达式（如 "CURRENT_TIMESTAMP"）
         */
        public Builder setExpr(String column, String rawSql) {
            rawExpressions.put(column, rawSql);
            return this;
        }

        /**
         * 跳过列：INSERT 中完全不出现该列，由数据库默认值/触发器/序列填充。
         * <p>用于自增主键、UUID 默认值等场景。</p>
         *
         * @param column 要跳过的列名
         */
        public Builder skip(String column) {
            skipColumns.add(column);
            return this;
        }

        public Builder addBatchRow(Map<String, Object> row) {
            batchMode = true;
            batchRows.add(new LinkedHashMap<String, Object>(row));
            return this;
        }

        public InsertStatement build() {
            if (table == null || table.isEmpty()) {
                throw new IllegalStateException("INSERT table is required");
            }
            if (!batchMode && values.isEmpty() && rawExpressions.isEmpty()) {
                throw new IllegalStateException("INSERT values cannot be empty");
            }
            if (batchMode && batchRows.isEmpty()) {
                throw new IllegalStateException("Batch INSERT rows cannot be empty");
            }
            return new InsertStatement(this);
        }
    }
}
