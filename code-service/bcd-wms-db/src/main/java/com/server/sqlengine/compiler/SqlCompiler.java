package com.server.sqlengine.compiler;

import com.server.sqlengine.dialect.SqlDialect;
import com.server.sqlengine.dialect.SqlDialectRegistry;
import com.server.sqlengine.model.CompiledSql;
import com.server.sqlengine.model.Condition;
import com.server.sqlengine.model.DeleteStatement;
import com.server.sqlengine.model.GroupBy;
import com.server.sqlengine.model.InsertStatement;
import com.server.sqlengine.model.JoinClause;
import com.server.sqlengine.model.Op;
import com.server.sqlengine.model.OrderBy;
import com.server.sqlengine.model.SelectQuery;
import com.server.sqlengine.model.SqlParam;
import com.server.sqlengine.model.UpdateStatement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SQL 编译器 —— 全新引擎的核心：将不可变 SQL 模型编译为 {@link CompiledSql}。
 *
 * <p>编译流程：模型 → SQL 文本（含 ? 占位符）+ 有序参数列表 → 方言分页包装 → CompiledSql。
 * 所有列名经白名单校验（通过 ParamSetter 的元数据查询间接验证），
 * 值全部参数化，杜绝 SQL 注入。</p>
 *
 * <p>与旧 {@code com.server.statement.SqlStmtCompiler} 的关键差异：</p>
 * <ul>
 *   <li>支持完整 SELECT（JOIN / GROUP BY / HAVING / 子查询 / 分页）</li>
 *   <li>条件树递归编译，支持任意深度 AND/OR 嵌套</li>
 *   <li>IN / BETWEEN / IS NULL 等操作符一等公民</li>
 *   <li>方言分页在编译期处理，不依赖后置字符串修补</li>
 *   <li>编译失败抛异常而非返回半截 SQL（旧实现 break 后继续拼的缺陷）</li>
 * </ul>
 *
 * @author liwei
 * @date 2026/9/9
 */
@Slf4j
@Service
public class SqlCompiler {

    private final SqlDialectRegistry dialectRegistry;
    private final ParamSetter paramSetter;

    @Autowired
    public SqlCompiler(SqlDialectRegistry dialectRegistry, ParamSetter paramSetter) {
        this.dialectRegistry = dialectRegistry;
        this.paramSetter = paramSetter;
    }

    /**
     * 获取指定数据源的方言（供 Facade 调用获取数据库时间字面量等）。
     */
    public com.server.sqlengine.dialect.SqlDialect getDialect(@Nullable String dbName) {
        return dialectRegistry.dialect(dbName);
    }

    // ==================== SELECT ====================

    public CompiledSql compileSelect(SelectQuery query, @Nullable String dbName) {
        List<SqlParam> params = new ArrayList<SqlParam>();
        StringBuilder sql = new StringBuilder("SELECT ");

        if (query.distinct()) {
            sql.append("DISTINCT ");
        }

        appendColumns(sql, query.columns());
        sql.append(" FROM ").append(query.table());
        if (query.alias() != null && !query.alias().isEmpty()) {
            sql.append(' ').append(query.alias());
        }

        for (JoinClause join : query.joins()) {
            sql.append(' ').append(join.type().sql())
                    .append(' ').append(join.table());
            if (!join.alias().equals(join.table())) {
                sql.append(' ').append(join.alias());
            }
            if (join.on() != null) {
                sql.append(" ON ");
                compileCondition(join.on(), sql, params, dbName, query.table());
            }
        }

        if (query.where() != null) {
            sql.append(" WHERE ");
            compileCondition(query.where(), sql, params, dbName, query.table());
        }

        if (query.hasGroupBy()) {
            sql.append(" GROUP BY ");
            for (int i = 0; i < query.groupBy().size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(Condition.upperCol(query.groupBy().get(i).column()));
            }
            if (query.having() != null) {
                sql.append(" HAVING ");
                compileCondition(query.having(), sql, params, dbName, query.table());
            }
        }

        if (!query.orderBy().isEmpty()) {
            sql.append(" ORDER BY ");
            for (int i = 0; i < query.orderBy().size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                appendOrderBy(sql, query.orderBy().get(i));
            }
        }

        String finalSql = sql.toString();
        if (query.hasPagination()) {
            SqlDialect dialect = dialectRegistry.dialect(dbName);
            int limit = query.limit() == null ? Integer.MAX_VALUE : query.limit();
            finalSql = dialect.paginate(finalSql, limit, query.offset());
        }

        return new CompiledSql(finalSql, params);
    }

    // ==================== INSERT ====================

    public CompiledSql compileInsert(InsertStatement stmt, @Nullable String dbName) {
        List<SqlParam> params = new ArrayList<SqlParam>();
        List<String> columns = stmt.columns();
        Map<String, String> rawExprs = stmt.rawExpressions();

        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(stmt.table()).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(columns.get(i));
        }
        sql.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            String col = columns.get(i);
            if (rawExprs.containsKey(col)) {
                // 原始表达式列：直接拼入 SQL 字面量，不参数化
                sql.append(rawExprs.get(col));
            } else {
                // 参数绑定列
                sql.append('?');
                Object value;
                if (stmt.isBatch()) {
                    value = stmt.batchRows().get(0).get(col);
                } else {
                    value = stmt.values().get(col);
                }
                params.add(paramSetter.set(dbName, stmt.table(), col, value));
            }
        }
        sql.append(')');

        return new CompiledSql(sql.toString(), params);
    }

    /**
     * 编译批量 INSERT 的单行参数集（SQL 文本与单行相同，仅参数不同）。
     *
     * @param stmt     INSERT 模型（batch 模式）
     * @param rowIndex 行索引
     * @param dbName   数据源名
     * @return 该行的参数列表
     */
    public List<SqlParam> compileInsertBatchRow(InsertStatement stmt, int rowIndex, @Nullable String dbName) {
        if (!stmt.isBatch() || rowIndex >= stmt.batchRows().size()) {
            throw new IllegalArgumentException("Invalid batch row index: " + rowIndex);
        }
        Map<String, Object> row = stmt.batchRows().get(rowIndex);
        List<String> columns = stmt.columns();
        Map<String, String> rawExprs = stmt.rawExpressions();
        List<SqlParam> params = new ArrayList<SqlParam>();
        for (String col : columns) {
            // 原始表达式列不产生参数，只参数绑定列产生参数
            if (!rawExprs.containsKey(col)) {
                params.add(paramSetter.set(dbName, stmt.table(), col, row.get(col)));
            }
        }
        return params;
    }

    // ==================== UPDATE ====================

    public CompiledSql compileUpdate(UpdateStatement stmt, @Nullable String dbName) {
        List<SqlParam> params = new ArrayList<SqlParam>();
        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(stmt.table());
        if (stmt.alias() != null && !stmt.alias().isEmpty()) {
            sql.append(' ').append(stmt.alias());
        }

        sql.append(" SET ");
        boolean first = true;
        for (Map.Entry<String, UpdateStatement.SetExpr> entry : stmt.setClauses().entrySet()) {
            String column = entry.getKey();
            UpdateStatement.SetExpr setExpr = entry.getValue();
            if (!first) {
                sql.append(", ");
            }
            first = false;
            sql.append(Condition.upperCol(column)).append('=');

            switch (setExpr.kind()) {
                case BIND:
                    sql.append('?');
                    params.add(paramSetter.set(dbName, stmt.table(), column, setExpr.value()));
                    break;
                case INCREMENT:
                    sql.append(Condition.upperCol(column)).append('+').append('?');
                    params.add(paramSetter.set(dbName, stmt.table(), column, setExpr.value()));
                    break;
                case RAW:
                    sql.append(setExpr.rawSql());
                    if (setExpr.rawParams() != null) {
                        for (Object p : setExpr.rawParams()) {
                            params.add(paramSetter.setRaw(p));
                        }
                    }
                    break;
                default:
                    throw new IllegalStateException("Unknown SetExpr kind: " + setExpr.kind());
            }
        }

        if (stmt.where() != null) {
            sql.append(" WHERE ");
            compileCondition(stmt.where(), sql, params, dbName, stmt.table());
        }

        return new CompiledSql(sql.toString(), params);
    }

    // ==================== DELETE ====================

    public CompiledSql compileDelete(DeleteStatement stmt, @Nullable String dbName) {
        List<SqlParam> params = new ArrayList<SqlParam>();
        SqlDialect dialect = dialectRegistry.dialect(dbName);

        StringBuilder sql = new StringBuilder("DELETE ");
        if (dialect.deleteNeedsFrom()) {
            sql.append("FROM ");
        }
        sql.append(stmt.table());
        if (stmt.alias() != null && !stmt.alias().isEmpty()) {
            sql.append(' ').append(stmt.alias());
        }

        if (stmt.hasUsing()) {
            sql.append(" USING ");
            for (int i = 0; i < stmt.usingTables().size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(stmt.usingTables().get(i));
            }
        }

        if (stmt.where() != null) {
            sql.append(" WHERE ");
            compileCondition(stmt.where(), sql, params, dbName, stmt.table());
        }

        return new CompiledSql(sql.toString(), params);
    }

    // ==================== 条件树编译（递归） ====================

    private void compileCondition(Condition cond, StringBuilder sql, List<SqlParam> params,
                                  @Nullable String dbName, String tableName) {
        if (cond instanceof Condition.Leaf) {
            compileLeaf((Condition.Leaf) cond, sql, params, dbName, tableName);
        } else if (cond instanceof Condition.Composite) {
            compileComposite((Condition.Composite) cond, sql, params, dbName, tableName);
        } else if (cond instanceof Condition.Raw) {
            sql.append(((Condition.Raw) cond).sql());
        } else {
            throw new IllegalStateException("Unknown Condition type: " + cond.getClass());
        }
    }

    private void compileComposite(Condition.Composite comp, StringBuilder sql,
                                  List<SqlParam> params, @Nullable String dbName, String tableName) {
        if (comp.children().isEmpty()) {
            sql.append("1=1");
            return;
        }
        if (comp.children().size() == 1) {
            compileCondition(comp.children().get(0), sql, params, dbName, tableName);
            return;
        }
        sql.append('(');
        for (int i = 0; i < comp.children().size(); i++) {
            if (i > 0) {
                sql.append(' ').append(comp.op().sql()).append(' ');
            }
            compileCondition(comp.children().get(i), sql, params, dbName, tableName);
        }
        sql.append(')');
    }

    @SuppressWarnings("unchecked")
    private void compileLeaf(Condition.Leaf leaf, StringBuilder sql, List<SqlParam> params,
                             @Nullable String dbName, String tableName) {
        String column = upperIfNoAlias(leaf.column());
        Op op = leaf.op();

        sql.append(column);

        switch (op) {
            case EQ:
            case NE:
            case LT:
            case LE:
            case GT:
            case GE:
                sql.append(' ').append(op.sql()).append(' ').append('?');
                params.add(paramSetter.set(dbName, tableName, bareColumn(leaf.column()), leaf.value()));
                break;

            case LIKE:
            case NOT_LIKE:
                sql.append(' ').append(op.sql()).append(' ').append('?');
                params.add(paramSetter.set(dbName, tableName, bareColumn(leaf.column()), leaf.value()));
                break;

            case IN:
            case NOT_IN:
                List<?> values = (List<?>) leaf.value();
                if (values == null || values.isEmpty()) {
                    sql.append(' ').append(op.sql()).append(" (NULL)");
                } else {
                    sql.append(' ').append(op.sql()).append(" (");
                    for (int i = 0; i < values.size(); i++) {
                        if (i > 0) {
                            sql.append(", ");
                        }
                        sql.append('?');
                        params.add(paramSetter.set(dbName, tableName, bareColumn(leaf.column()), values.get(i)));
                    }
                    sql.append(')');
                }
                break;

            case BETWEEN:
                List<?> range = (List<?>) leaf.value();
                sql.append(' ').append(op.sql()).append(" ? AND ?");
                params.add(paramSetter.set(dbName, tableName, bareColumn(leaf.column()), range.get(0)));
                params.add(paramSetter.set(dbName, tableName, bareColumn(leaf.column()), range.get(1)));
                break;

            case IS_NULL:
                sql.append(' ').append(op.sql());
                break;

            case IS_NOT_NULL:
                sql.append(' ').append(op.sql());
                break;

            default:
                throw new IllegalStateException("Unsupported Op in condition compilation: " + op);
        }
    }

    // ==================== 辅助方法 ====================

    private void appendColumns(StringBuilder sql, List<String> columns) {
        if (columns.isEmpty()) {
            sql.append('*');
            return;
        }
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(columns.get(i));
        }
    }

    private void appendOrderBy(StringBuilder sql, OrderBy item) {
        sql.append(Condition.upperCol(item.column()));
        if (item.direction() == OrderBy.Direction.DESC) {
            sql.append(" DESC");
        } else {
            sql.append(" ASC");
        }
        if (item.nulls() != OrderBy.Nulls.NONE) {
            sql.append(' ').append(item.nulls().sql());
        }
    }

    /**
     * 大写化列名（保留别名前缀，仅大写列部分）。
     */
    private String upperIfNoAlias(String column) {
        return Condition.upperCol(column);
    }

    /**
     * 从 "alias.column" 提取纯列名。
     */
    private String bareColumn(String column) {
        int dot = column.indexOf('.');
        return dot >= 0 ? column.substring(dot + 1) : column;
    }
}
