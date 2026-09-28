package com.server.sqlengine.model;

/**
 * SQL ORDER BY 子句项 —— 不可变。
 *
 * @author liwei
 * @date 2026/9/9
 */
public final class OrderBy {

    public enum Direction {
        ASC, DESC
    }

    public enum Nulls {
        FIRST("NULLS FIRST"),
        LAST("NULLS LAST"),
        NONE("");

        private final String sql;

        Nulls(String sql) {
            this.sql = sql;
        }

        public String sql() {
            return sql;
        }
    }

    private final String column;
    private final Direction direction;
    private final Nulls nulls;

    public OrderBy(String column, Direction direction, Nulls nulls) {
        this.column = column;
        this.direction = direction == null ? Direction.ASC : direction;
        this.nulls = nulls == null ? Nulls.NONE : nulls;
    }

    public OrderBy(String column, Direction direction) {
        this(column, direction, Nulls.NONE);
    }

    public OrderBy(String column) {
        this(column, Direction.ASC, Nulls.NONE);
    }

    public static OrderBy asc(String column) {
        return new OrderBy(column, Direction.ASC);
    }

    public static OrderBy desc(String column) {
        return new OrderBy(column, Direction.DESC);
    }

    public static OrderBy ascNullsFirst(String column) {
        return new OrderBy(column, Direction.ASC, Nulls.FIRST);
    }

    public static OrderBy descNullsLast(String column) {
        return new OrderBy(column, Direction.DESC, Nulls.LAST);
    }

    public String column() {
        return column;
    }

    public Direction direction() {
        return direction;
    }

    public Nulls nulls() {
        return nulls;
    }
}
