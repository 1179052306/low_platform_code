package com.server.sqlengine.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * SQL 条件模型 —— 不可变树形结构，支持 AND/OR 嵌套。
 * <p>取代旧实现中平铺中缀数组的解析方式（i%2 位置契约），条件树结构显式表达逻辑层级，
 * 消除静默错位风险。</p>
 *
 * <p>两种节点：</p>
 * <ul>
 *   <li><b>叶子节点</b>：单个比较条件（field + op + value/values）</li>
 *   <li><b>组合节点</b>：AND/OR 连接的子条件列表</li>
 * </ul>
 *
 * <p>静态工厂方法提供流式构造：</p>
 * <pre>{@code
 * Condition where = Condition.and(
 *     Condition.eq("status", "active"),
 *     Condition.or(
 *         Condition.gt("age", 18),
 *         Condition.like("name", "张%")
 *     ),
 *     Condition.in("dept_id", Arrays.asList(1, 2, 3))
 * );
 * }</pre>
 *
 * @author liwei
 * @date 2026/9/9
 */
public abstract class Condition {

    Condition() {
    }

    // ==================== 组合节点 ====================

    public static Condition and(Condition... conditions) {
        return composite(LogicOp.AND, conditions);
    }

    public static Condition or(Condition... conditions) {
        return composite(LogicOp.OR, conditions);
    }

    private static Condition composite(LogicOp op, Condition... conditions) {
        List<Condition> list = new ArrayList<Condition>();
        for (Condition c : conditions) {
            if (c != null) {
                list.add(c);
            }
        }
        return new Composite(op, Collections.unmodifiableList(list));
    }

    // ==================== 叶子节点：等值/范围 ====================

    public static Condition eq(String column, Object value) {
        return leaf(column, Op.EQ, value);
    }

    public static Condition ne(String column, Object value) {
        return leaf(column, Op.NE, value);
    }

    public static Condition lt(String column, Object value) {
        return leaf(column, Op.LT, value);
    }

    public static Condition le(String column, Object value) {
        return leaf(column, Op.LE, value);
    }

    public static Condition gt(String column, Object value) {
        return leaf(column, Op.GT, value);
    }

    public static Condition ge(String column, Object value) {
        return leaf(column, Op.GE, value);
    }

    // ==================== 叶子节点：模糊匹配 ====================

    public static Condition like(String column, String value) {
        return leaf(column, Op.LIKE, value);
    }

    public static Condition notLike(String column, String value) {
        return leaf(column, Op.NOT_LIKE, value);
    }

    // ==================== 叶子节点：集合操作 ====================

    public static Condition in(String column, List<?> values) {
        return new Leaf(column, Op.IN, values, null);
    }

    public static Condition in(String column, Object... values) {
        return new Leaf(column, Op.IN, Arrays.asList(values), null);
    }

    public static Condition notIn(String column, List<?> values) {
        return new Leaf(column, Op.NOT_IN, values, null);
    }

    public static Condition notIn(String column, Object... values) {
        return new Leaf(column, Op.NOT_IN, Arrays.asList(values), null);
    }

    // ==================== 叶子节点：区间 ====================

    public static Condition between(String column, Object low, Object high) {
        return new Leaf(column, Op.BETWEEN, Arrays.asList(low, high), null);
    }

    // ==================== 叶子节点：空值判断 ====================

    public static Condition isNull(String column) {
        return new Leaf(column, Op.IS_NULL, null, null);
    }

    public static Condition isNotNull(String column) {
        return new Leaf(column, Op.IS_NOT_NULL, null, null);
    }

    // ==================== 叶子节点：带别名 ====================

    private static Condition leaf(String column, Op op, Object value) {
        return new Leaf(column, op, value, null);
    }

    /**
     * 创建带表别名的叶子条件副本。
     */
    public static Condition of(String alias, String column, Op op, Object value) {
        String qualified = alias == null || alias.isEmpty() ? column : alias + "." + column;
        return new Leaf(qualified, op, value, null);
    }

    // ==================== 原始 SQL 条件（JOIN ON 列间比较） ====================

    /**
     * 创建原始 SQL 条件 —— 不参数化，用于 JOIN ON 的列间比较（如 a.id = b.aid）。
     * <p><b>仅用于可信的内部场景</b>（JOIN ON），不用于接收外部输入。</p>
     */
    public static Condition raw(String sqlClause) {
        return new Raw(sqlClause);
    }

    /**
     * 列间等值比较（JOIN ON 常用）：column1 = column2，不参数化。
     */
    public static Condition columnEq(String column1, String column2) {
        return new Raw(upperCol(column1) + " = " + upperCol(column2));
    }

    /**
     * 大写化列名（含别名前缀整体大写，与旧 toUpperCase 约定一致）。
     */
    public static String upperCol(String column) {
        if (column == null) {
            return null;
        }
        return column.toUpperCase(Locale.ROOT);
    }

    // ==================== 内部类型 ====================

    public enum LogicOp {
        AND("AND"), OR("OR");

        private final String sql;

        LogicOp(String sql) {
            this.sql = sql;
        }

        public String sql() {
            return sql;
        }
    }

    /**
     * 叶子条件节点。
     */
    public static final class Leaf extends Condition {
        private final String column;
        private final Op op;
        private final Object value;
        private final String typeHint;

        Leaf(String column, Op op, Object value, String typeHint) {
            this.column = column;
            this.op = op;
            this.value = value;
            this.typeHint = typeHint;
        }

        public String column() {
            return column;
        }

        public Op op() {
            return op;
        }

        public Object value() {
            return value;
        }

        public String typeHint() {
            return typeHint;
        }

        public Leaf withTypeHint(String hint) {
            return new Leaf(column, op, value, hint);
        }

        public Leaf withAlias(String alias) {
            int dot = column.indexOf('.');
            String bare = dot >= 0 ? column.substring(dot + 1) : column;
            String qualified = (alias == null || alias.isEmpty()) ? bare : alias + "." + bare;
            return new Leaf(qualified, op, value, typeHint);
        }
    }

    /**
     * 原始 SQL 条件节点 —— 直接嵌入 SQL 文本，不参数化。
     * 用于 JOIN ON 的列间比较（如 a.id = b.aid）。
     */
    public static final class Raw extends Condition {
        private final String sql;

        Raw(String sql) {
            this.sql = sql;
        }

        public String sql() {
            return sql;
        }
    }

    /**
     * 组合条件节点（AND/OR）。
     */
    public static final class Composite extends Condition {
        private final LogicOp op;
        private final List<Condition> children;

        Composite(LogicOp op, List<Condition> children) {
            this.op = op;
            this.children = children;
        }

        public LogicOp op() {
            return op;
        }

        public List<Condition> children() {
            return children;
        }
    }
}
