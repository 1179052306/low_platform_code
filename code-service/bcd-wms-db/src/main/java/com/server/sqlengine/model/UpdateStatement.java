package com.server.sqlengine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UPDATE 语句模型 —— 不可变。
 * <p>SET 子句支持值绑定（column = ?）和原始表达式（column = expr），
 * WHERE 子句使用 {@link Condition} 条件树。</p>
 *
 * @author liwei
 * @date 2026/9/9
 */
public final class UpdateStatement {

    private final String table;
    private final String alias;
    private final Map<String, SetExpr> setClauses;
    private final Condition where;
    private final List<JoinClause> joins;

    private UpdateStatement(Builder b) {
        this.table = b.table;
        this.alias = b.alias;
        this.setClauses = Collections.unmodifiableMap(
                new LinkedHashMap<String, SetExpr>(b.setClauses));
        this.where = b.where;
        this.joins = Collections.unmodifiableList(new ArrayList<JoinClause>(b.joins));
    }

    public String table() {
        return table;
    }

    public String alias() {
        return alias;
    }

    public Map<String, SetExpr> setClauses() {
        return setClauses;
    }

    public Condition where() {
        return where;
    }

    public List<JoinClause> joins() {
        return joins;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String table;
        private String alias;
        private final LinkedHashMap<String, SetExpr> setClauses = new LinkedHashMap<String, SetExpr>();
        private Condition where;
        private final List<JoinClause> joins = new ArrayList<JoinClause>();

        public Builder table(String table) {
            this.table = table;
            return this;
        }

        public Builder table(String table, String alias) {
            this.table = table;
            this.alias = alias;
            return this;
        }

        public Builder set(String column, Object value) {
            setClauses.put(column, SetExpr.bind(value));
            return this;
        }

        public Builder setAll(Map<String, Object> map) {
            for (Map.Entry<String, Object> e : map.entrySet()) {
                setClauses.put(e.getKey(), SetExpr.bind(e.getValue()));
            }
            return this;
        }

        public Builder setExpr(String column, String rawSql, Object... params) {
            setClauses.put(column, SetExpr.raw(rawSql, params));
            return this;
        }

        public Builder setIncrement(String column, Number delta) {
            setClauses.put(column, SetExpr.increment(delta));
            return this;
        }

        public Builder join(JoinClause join) {
            joins.add(join);
            return this;
        }

        public Builder where(Condition condition) {
            this.where = condition;
            return this;
        }

        public Builder andWhere(Condition condition) {
            if (condition == null) {
                return this;
            }
            this.where = this.where == null ? condition : Condition.and(this.where, condition);
            return this;
        }

        public UpdateStatement build() {
            if (table == null || table.isEmpty()) {
                throw new IllegalStateException("UPDATE table is required");
            }
            if (setClauses.isEmpty()) {
                throw new IllegalStateException("UPDATE SET clause cannot be empty");
            }
            if (where == null) {
                throw new IllegalStateException("UPDATE WHERE clause is required (use setUnsafeNoWhere for intentional full-table updates)");
            }
            return new UpdateStatement(this);
        }
    }

    /**
     * SET 子句项 —— 绑定值或原始表达式。
     */
    public static final class SetExpr {
        public enum Kind {
            BIND, RAW, INCREMENT
        }

        private final Kind kind;
        private final Object value;
        private final String rawSql;
        private final Object[] rawParams;

        private SetExpr(Kind kind, Object value, String rawSql, Object[] rawParams) {
            this.kind = kind;
            this.value = value;
            this.rawSql = rawSql;
            this.rawParams = rawParams;
        }

        static SetExpr bind(Object value) {
            return new SetExpr(Kind.BIND, value, null, null);
        }

        static SetExpr raw(String rawSql, Object[] params) {
            return new SetExpr(Kind.RAW, null, rawSql, params);
        }

        static SetExpr increment(Number delta) {
            return new SetExpr(Kind.INCREMENT, delta, null, null);
        }

        public Kind kind() {
            return kind;
        }

        public Object value() {
            return value;
        }

        public String rawSql() {
            return rawSql;
        }

        public Object[] rawParams() {
            return rawParams;
        }
    }
}
