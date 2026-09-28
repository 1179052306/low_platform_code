package com.server.sqlengine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SELECT 查询模型 —— 不可变。
 * <p>描述一次 SELECT 的完整结构：列选择、表源、JOIN、WHERE 树、GROUP BY、HAVING、ORDER BY、分页。
 * 经 {@code SqlCompiler} 编译为 {@link CompiledSql}。</p>
 *
 * @author liwei
 * @date 2026/9/9
 */
public final class SelectQuery {

    private final List<String> columns;
    private final String table;
    private final String alias;
    private final List<JoinClause> joins;
    private final Condition where;
    private final List<GroupBy> groupBy;
    private final Condition having;
    private final List<OrderBy> orderBy;
    private final Integer limit;
    private final Integer offset;
    private final boolean distinct;

    private SelectQuery(Builder b) {
        this.columns = Collections.unmodifiableList(new ArrayList<String>(b.columns));
        this.table = b.table;
        this.alias = b.alias;
        this.joins = Collections.unmodifiableList(new ArrayList<JoinClause>(b.joins));
        this.where = b.where;
        this.groupBy = Collections.unmodifiableList(new ArrayList<GroupBy>(b.groupBy));
        this.having = b.having;
        this.orderBy = Collections.unmodifiableList(new ArrayList<OrderBy>(b.orderBy));
        this.limit = b.limit;
        this.offset = b.offset;
        this.distinct = b.distinct;
    }

    public List<String> columns() {
        return columns;
    }

    public String table() {
        return table;
    }

    public String alias() {
        return alias;
    }

    public List<JoinClause> joins() {
        return joins;
    }

    public Condition where() {
        return where;
    }

    public List<GroupBy> groupBy() {
        return groupBy;
    }

    public Condition having() {
        return having;
    }

    public List<OrderBy> orderBy() {
        return orderBy;
    }

    public Integer limit() {
        return limit;
    }

    public Integer offset() {
        return offset;
    }

    public boolean distinct() {
        return distinct;
    }

    public boolean hasPagination() {
        return limit != null || offset != null;
    }

    public boolean hasGroupBy() {
        return !groupBy.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<String> columns = new ArrayList<String>();
        private String table;
        private String alias;
        private final List<JoinClause> joins = new ArrayList<JoinClause>();
        private Condition where;
        private final List<GroupBy> groupBy = new ArrayList<GroupBy>();
        private Condition having;
        private final List<OrderBy> orderBy = new ArrayList<OrderBy>();
        private Integer limit;
        private Integer offset;
        private boolean distinct;

        public Builder column(String column) {
            columns.add(column);
            return this;
        }

        public Builder columns(String... cols) {
            for (String c : cols) {
                columns.add(c);
            }
            return this;
        }

        public Builder columns(List<String> cols) {
            columns.addAll(cols);
            return this;
        }

        public Builder selectAll() {
            columns.add("*");
            return this;
        }

        public Builder distinct() {
            this.distinct = true;
            return this;
        }

        public Builder from(String table) {
            this.table = table;
            return this;
        }

        public Builder from(String table, String alias) {
            this.table = table;
            this.alias = alias;
            return this;
        }

        public Builder join(JoinClause join) {
            joins.add(join);
            return this;
        }

        public Builder innerJoin(String table, String alias, Condition on) {
            joins.add(JoinClause.innerJoin(table, alias, on));
            return this;
        }

        public Builder leftJoin(String table, String alias, Condition on) {
            joins.add(JoinClause.leftJoin(table, alias, on));
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

        public Builder orWhere(Condition condition) {
            if (condition == null) {
                return this;
            }
            this.where = this.where == null ? condition : Condition.or(this.where, condition);
            return this;
        }

        public Builder groupBy(String column) {
            groupBy.add(new GroupBy(column));
            return this;
        }

        public Builder having(Condition condition) {
            this.having = condition;
            return this;
        }

        public Builder orderBy(OrderBy item) {
            orderBy.add(item);
            return this;
        }

        public Builder orderByAsc(String column) {
            orderBy.add(OrderBy.asc(column));
            return this;
        }

        public Builder orderByDesc(String column) {
            orderBy.add(OrderBy.desc(column));
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder offset(int offset) {
            this.offset = offset;
            return this;
        }

        public Builder page(int pageNum, int pageSize) {
            this.limit = pageSize;
            this.offset = (pageNum - 1) * pageSize;
            return this;
        }

        public SelectQuery build() {
            if (table == null || table.isEmpty()) {
                throw new IllegalStateException("FROM table is required");
            }
            if (columns.isEmpty()) {
                columns.add("*");
            }
            return new SelectQuery(this);
        }
    }
}
