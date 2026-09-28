package com.server.sqlengine.model;

/**
 * SQL 比较操作符枚举 —— 全新引擎的操作符唯一来源。
 * <p>覆盖等值/范围/模糊/集合/空值/存在性六大类，取代旧 {@code com.server.statement.Op}
 * 的有限集合并消除散落各处的字符串符号判断。</p>
 *
 * @author liwei
 * @date 2026/9/9
 */
public enum Op {

    // ---- 等值比较 ----
    EQ("="),
    NE("<>"),

    // ---- 范围比较 ----
    LT("<"),
    LE("<="),
    GT(">"),
    GE(">="),

    // ---- 模糊匹配 ----
    LIKE("LIKE"),
    NOT_LIKE("NOT LIKE"),

    // ---- 集合操作 ----
    IN("IN"),
    NOT_IN("NOT IN"),

    // ---- 区间 ----
    BETWEEN("BETWEEN"),

    // ---- 空值判断 ----
    IS_NULL("IS NULL"),
    IS_NOT_NULL("IS NOT NULL"),

    // ---- 存在性（子查询） ----
    EXISTS("EXISTS"),
    NOT_EXISTS("NOT EXISTS");

    private final String sql;

    Op(String sql) {
        this.sql = sql;
    }

    public String sql() {
        return sql;
    }

    public boolean isNullCheck() {
        return this == IS_NULL || this == IS_NOT_NULL;
    }

    public boolean isSetOp() {
        return this == IN || this == NOT_IN;
    }

    public boolean isBetween() {
        return this == BETWEEN;
    }

    public boolean isExists() {
        return this == EXISTS || this == NOT_EXISTS;
    }

    public boolean isLike() {
        return this == LIKE || this == NOT_LIKE;
    }

    public boolean needsValue() {
        return !isNullCheck() && !isExists();
    }
}
