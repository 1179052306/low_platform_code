package com.server.sqlengine.model;

/**
 * SQL GROUP BY 子句项 —— 不可变。
 *
 * @author liwei
 * @date 2026/9/9
 */
public final class GroupBy {

    private final String column;

    public GroupBy(String column) {
        this.column = column;
    }

    public String column() {
        return column;
    }
}
