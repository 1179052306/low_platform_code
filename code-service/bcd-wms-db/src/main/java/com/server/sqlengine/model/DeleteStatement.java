package com.server.sqlengine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DELETE 语句模型 —— 不可变。
 * <p>WHERE 条件为必填项（防止全表删除），支持 USING 子句做多表关联删除。</p>
 *
 * @author liwei
 * @date 2026/9/9
 */
public final class DeleteStatement {

    private final String table;
    private final String alias;
    private final Condition where;
    private final List<String> usingTables;

    private DeleteStatement(Builder b) {
        this.table = b.table;
        this.alias = b.alias;
        this.where = b.where;
        this.usingTables = Collections.unmodifiableList(new ArrayList<String>(b.usingTables));
    }

    public String table() {
        return table;
    }

    public String alias() {
        return alias;
    }

    public Condition where() {
        return where;
    }

    public List<String> usingTables() {
        return usingTables;
    }

    public boolean hasUsing() {
        return !usingTables.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String table;
        private String alias;
        private Condition where;
        private final List<String> usingTables = new ArrayList<String>();

        public Builder from(String table) {
            this.table = table;
            return this;
        }

        public Builder from(String table, String alias) {
            this.table = table;
            this.alias = alias;
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

        public Builder using(String table) {
            usingTables.add(table);
            return this;
        }

        public DeleteStatement build() {
            if (table == null || table.isEmpty()) {
                throw new IllegalStateException("DELETE table is required");
            }
            if (where == null) {
                throw new IllegalStateException("DELETE WHERE clause is required (prevents accidental full-table delete)");
            }
            return new DeleteStatement(this);
        }
    }
}
