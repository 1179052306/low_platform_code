package com.server.sqlengine.model;

/**
 * SQL 参数绑定模型 —— 编译产物中占位符 {@code ?} 对应的参数值。
 * <p>携带列名/类型提示供方言层做类型转换（如 PG 的 to_number），但核心仅是值。</p>
 *
 * @author liwei
 * @date 2026/9/9
 */
public final class SqlParam {

    private final String columnName;
    private final Object value;
    private final String typeHint;

    public SqlParam(String columnName, Object value) {
        this(columnName, value, null);
    }

    public SqlParam(String columnName, Object value, String typeHint) {
        this.columnName = columnName;
        this.value = value;
        this.typeHint = typeHint;
    }

    public String columnName() {
        return columnName;
    }

    public Object value() {
        return value;
    }

    public String typeHint() {
        return typeHint;
    }
}
