package com.server.sqlengine.model;

/**
 * SQL JOIN 子句模型 —— 不可变。
 *
 * @author liwei
 * @date 2026/9/9
 */
public final class JoinClause {

    public enum Type {
        INNER("INNER JOIN"),
        LEFT("LEFT JOIN"),
        RIGHT("RIGHT JOIN"),
        FULL("FULL OUTER JOIN");

        private final String sql;

        Type(String sql) {
            this.sql = sql;
        }

        public String sql() {
            return sql;
        }
    }

    private final Type type;
    private final String table;
    private final String alias;
    private final Condition on;

    public JoinClause(Type type, String table, String alias, Condition on) {
        this.type = type;
        this.table = table;
        this.alias = alias == null || alias.isEmpty() ? table : alias;
        this.on = on;
    }

    public static JoinClause innerJoin(String table, String alias, Condition on) {
        return new JoinClause(Type.INNER, table, alias, on);
    }

    public static JoinClause leftJoin(String table, String alias, Condition on) {
        return new JoinClause(Type.LEFT, table, alias, on);
    }

    public static JoinClause rightJoin(String table, String alias, Condition on) {
        return new JoinClause(Type.RIGHT, table, alias, on);
    }

    public static JoinClause fullJoin(String table, String alias, Condition on) {
        return new JoinClause(Type.FULL, table, alias, on);
    }

    public Type type() {
        return type;
    }

    public String table() {
        return table;
    }

    public String alias() {
        return alias;
    }

    public Condition on() {
        return on;
    }
}
