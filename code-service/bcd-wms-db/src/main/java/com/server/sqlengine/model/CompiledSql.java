package com.server.sqlengine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SQL 编译产物 —— 不可变，包含最终 SQL 文本（含 {@code ?} 占位符）和有序参数列表。
 * <p>所有 Builder 产出的模型经 {@code SqlCompiler} 编译后得到本对象，
 * 可直接交由 JDBC {@code PreparedStatement} 执行。</p>
 *
 * @author liwei
 * @date 2026/9/9
 */
public final class CompiledSql {

    private final String sql;
    private final List<SqlParam> params;

    public CompiledSql(String sql, List<SqlParam> params) {
        this.sql = sql;
        this.params = params == null
                ? Collections.<SqlParam>emptyList()
                : Collections.unmodifiableList(new ArrayList<SqlParam>(params));
    }

    public String sql() {
        return sql;
    }

    public List<SqlParam> params() {
        return params;
    }

    public boolean isEmpty() {
        return sql == null || sql.isEmpty();
    }
}
