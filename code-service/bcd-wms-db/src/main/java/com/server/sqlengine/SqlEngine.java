package com.server.sqlengine;

import com.server.sqlengine.compiler.SqlCompiler;
import com.server.sqlengine.model.CompiledSql;
import com.server.sqlengine.model.DeleteStatement;
import com.server.sqlengine.model.InsertStatement;
import com.server.sqlengine.model.SelectQuery;
import com.server.sqlengine.model.UpdateStatement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * SQL 引擎门面 —— 全新 SQL 生成引擎的统一入口。
 *
 * <p>使用方式：</p>
 * <pre>{@code
 * @Autowired
 * private SqlEngine sqlEngine;
 *
 * // SELECT
 * CompiledSql sql = sqlEngine.select(SelectQuery.builder()
 *     .from("users", "u")
 *     .columns("u.id", "u.name", "d.dept_name")
 *     .leftJoin("dept", "d", Condition.eq("u.dept_id", "d.id"))
 *     .where(Condition.and(
 *         Condition.eq("u.status", "active"),
 *         Condition.gt("u.age", 18)
 *     ))
 *     .orderByDesc("u.create_time")
 *     .page(1, 20)
 *     .build(), "default");
 *
 * // INSERT
 * CompiledSql sql = sqlEngine.insert(InsertStatement.builder()
 *     .into("users")
 *     .set("name", "张三")
 *     .set("age", 25)
 *     .build(), "default");
 *
 * // UPDATE
 * CompiledSql sql = sqlEngine.update(UpdateStatement.builder()
 *     .table("users")
 *     .set("status", "inactive")
 *     .where(Condition.eq("id", 100))
 *     .build(), "default");
 *
 * // DELETE
 * CompiledSql sql = sqlEngine.delete(DeleteStatement.builder()
 *     .from("users")
 *     .where(Condition.eq("id", 100))
 *     .build(), "default");
 * }</pre>
 *
 * <p>所有方法返回 {@link CompiledSql}（不可变 SQL + 有序参数列表），
 * 可直接交由 JDBC {@code PreparedStatement} 执行。</p>
 *
 * @author liwei
 * @date 2026/9/9
 */
@Service
public class SqlEngine {

    private final SqlCompiler compiler;

    @Autowired
    public SqlEngine(SqlCompiler compiler) {
        this.compiler = compiler;
    }

    public CompiledSql select(SelectQuery query, @Nullable String dbName) {
        return compiler.compileSelect(query, dbName);
    }

    public CompiledSql insert(InsertStatement stmt, @Nullable String dbName) {
        return compiler.compileInsert(stmt, dbName);
    }

    public CompiledSql update(UpdateStatement stmt, @Nullable String dbName) {
        return compiler.compileUpdate(stmt, dbName);
    }

    public CompiledSql delete(DeleteStatement stmt, @Nullable String dbName) {
        return compiler.compileDelete(stmt, dbName);
    }
}
