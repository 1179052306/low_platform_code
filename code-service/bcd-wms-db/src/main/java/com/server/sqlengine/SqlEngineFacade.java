package com.server.sqlengine;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import com.server.sqlengine.executor.SqlExecutor;
import com.server.sqlengine.json.JsonConditionConverter;
import com.server.sqlengine.model.CompiledSql;
import com.server.sqlengine.model.Condition;
import com.server.sqlengine.model.DeleteStatement;
import com.server.sqlengine.model.InsertStatement;

import com.server.sqlengine.model.SelectQuery;
import com.server.sqlengine.model.SqlParam;
import com.server.sqlengine.model.UpdateStatement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL 引擎门面 —— 接受前端 JSON，编译为参数化 SQL，通过自有 {@link SqlExecutor} 执行。
 *
 * <p>
 * <b>不依赖旧 DbHelp</b>，全部 JDBC 操作由 {@link SqlExecutor} 独立完成。
 * </p>
 *
 * <p>
 * <b>前端调用示例</b>：
 * </p>
 *
 * <p>
 * 1. 查询（SELECT）
 * </p>
 * 
 * <pre>{@code
 * JSONObject req = new JSONObject();
 * req.put("tableName", "users");
 * req.put("columns", new JSONArray(Arrays.asList("id", "name")));
 * req.put("conditions", conditionsArray);
 * req.put("page", 1);
 * req.put("pageSize", 20);
 * JSONArray result = sqlEngineFacade.query(req, "default");
 * }</pre>
 *
 * <p>
 * 2. 新增（INSERT）
 * </p>
 * 
 * <pre>{@code
 * JSONObject req = new JSONObject();
 * req.put("tableName", "users");
 * req.put("data", dataJsonObject);
 * int rows = sqlEngineFacade.insert(req, "default");
 * }</pre>
 *
 * <p>
 * 3. 修改（UPDATE）
 * </p>
 * 
 * <pre>{@code
 * JSONObject req = new JSONObject();
 * req.put("tableName", "users");
 * req.put("data", dataJsonObject);
 * req.put("conditions", conditionsArray);
 * int rows = sqlEngineFacade.update(req, "default");
 * }</pre>
 *
 * <p>
 * 4. 删除（DELETE）
 * </p>
 * 
 * <pre>{@code
 * JSONObject req = new JSONObject();
 * req.put("tableName", "users");
 * req.put("conditions", conditionsArray);
 * int rows = sqlEngineFacade.delete(req, "default");
 * }</pre>
 *
 * @author liwei
 * @date 2026/9/9
 */
@Slf4j
@Service
public class SqlEngineFacade {

    private final SqlEngine sqlEngine;
    private final JsonConditionConverter converter;
    private final SqlExecutor executor;
    private final com.server.sqlengine.compiler.SqlCompiler compiler;
    private final com.server.sqlengine.validator.SqlFieldValidator validator;
    private final com.server.sqlengine.template.SqlTemplateEngine templateEngine;

    @Autowired
    public SqlEngineFacade(SqlEngine sqlEngine, JsonConditionConverter converter,
            SqlExecutor executor,
            com.server.sqlengine.compiler.SqlCompiler compiler,
            com.server.sqlengine.validator.SqlFieldValidator validator,
            com.server.sqlengine.template.SqlTemplateEngine templateEngine) {
        this.sqlEngine = sqlEngine;
        this.converter = converter;
        this.executor = executor;
        this.compiler = compiler;
        this.validator = validator;
        this.templateEngine = templateEngine;
    }

    // ==================== 查询 ====================

    /**
     * JSON 驱动查询 —— 接受前端 JSONObject，编译为参数化 SELECT 并执行。
     *
     * <p>
     * 统一入口：含 {@code sqlTemplate} 时直接用作 baseSql，
     * 否则自动生成 SELECT ... FROM ... WHERE 1=1。两条路径统一走
     * {@link com.server.sqlengine.template.SqlTemplateEngine#render} 处理
     * Rpc/Rps/其余条件。
     * </p>
     */
    public ResMsg query(JSONObject req, @Nullable String dbName) {
        try {
            CompiledSql compiled = compileQuery(req, dbName);
            log.debug("SQL引擎-查询: {}", compiled.sql());
            JSONArray data = executor.query(dbName, compiled);
            ResMsg r = new ResMsg();
            r.setData(data);
            return r;
        } catch (Exception e) {
            log.error("SQL引擎-查询失败", e);
            ResMsg r = new ResMsg();
            r.setRes(false);
            r.setMsg(e.getMessage());
            return r;
        }
    }

    /**
     * 查询编译核心 —— query / batchQuery 共用。
     *
     * <p>
     * 统一流程：① 确定 baseSql（用户模板或自动生成 SELECT ... WHERE 1=1）
     * → ② templateEngine.render 处理 Rpc/Rps/其余条件 → ③ applyQueryOptions 追加
     * groupBy/orderBy/page。
     * 支持 columns / distinct / alias / conditions / rpsTemplates / groupBy / orderBy
     * / page /
     * pageSize 全部能力。
     * </p>
     */
    private CompiledSql compileQuery(JSONObject req, @Nullable String dbName) throws Exception {
        // 1. 确定 baseSql：有 sqlTemplate 用用户模板，否则自动生成 SELECT ... FROM ... WHERE 1=1
        String sqlTemplate = req.getString("sqlTemplate");
        String baseSql;
        String defaultAlias = req.getString("defaultAlias");

        if (sqlTemplate != null && !sqlTemplate.isEmpty()) {
            baseSql = sqlTemplate;
        } else {
            String tableName = req.getString("tableName");
            if (tableName == null || tableName.isEmpty()) {
                throw new IllegalArgumentException("tableName 不能为空");
            }

            // 字段合法性校验：表名 + 列名（格式 + 元数据）
            JSONArray columns = req.getJSONArray("columns");
            List<String> columnList = new ArrayList<String>();
            if (columns != null && !columns.isEmpty()) {
                for (int i = 0; i < columns.size(); i++) {
                    columnList.add(columns.getString(i));
                }
            }
            JSONArray conditions = req.getJSONArray("conditions");
            List<String> conditionCols = converter.extractColumnNames(conditions);
            List<String> allCols = new ArrayList<String>(columnList);
            allCols.addAll(conditionCols);
            validator.validateTableAndColumns(dbName, tableName, allCols);

            // 表别名：优先用请求中显式指定的 alias，否则从条件 JSON 的 TableAlias 自动提取
            String alias = req.getString("alias");
            if (alias == null || alias.isEmpty()) {
                alias = converter.extractAlias(conditions);
            }
            if (defaultAlias == null || defaultAlias.isEmpty()) {
                defaultAlias = alias;
            }

            // 自动生成 baseSql
            StringBuilder sb = new StringBuilder("SELECT ");
            if (req.getBooleanValue("distinct")) {
                sb.append("DISTINCT ");
            }
            if (columns != null && !columns.isEmpty()) {
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(columns.getString(i));
                }
            } else {
                sb.append("*");
            }
            sb.append(" FROM ").append(tableName);
            if (alias != null && !alias.isEmpty()) {
                sb.append(" ").append(alias);
            }
            sb.append(" WHERE 1=1");
            baseSql = sb.toString();
        }

        // 2. 解析 Rps 模板条件
        JSONArray conditions = req.getJSONArray("conditions");
        JSONObject rpsJson = req.getJSONObject("rpsTemplates");
        java.util.Map<String, com.server.sqlengine.template.SqlTemplateCondition> rpsMap = null;
        if (rpsJson != null) {
            rpsMap = new java.util.LinkedHashMap<String, com.server.sqlengine.template.SqlTemplateCondition>();
            for (String field : rpsJson.keySet()) {
                rpsMap.put(field.toUpperCase(),
                        new com.server.sqlengine.template.SqlTemplateCondition(field, rpsJson.getString(field)));
            }
        }

        // 3. 统一走模板引擎：Rpc 占位符替换 → Rps 独立追加 → 其余条件追加
        com.server.sqlengine.template.SqlTemplateEngine.TemplateResult result = templateEngine.render(baseSql,
                conditions, rpsMap, defaultAlias);
        CompiledSql compiled = new CompiledSql(result.sql(), result.params());

        // 4. 追加 groupBy / orderBy / page
        compiled = applyQueryOptions(compiled, req);

        return compiled;
    }

    /**
     * 给 render 产出的 SQL 追加 groupBy / orderBy / page / limit。
     *
     * <p>
     * render 产出的 SQL 是纯文本，故在此直接拼接。
     * 分页用 LIMIT/OFFSET 语法（PostgreSQL/MySQL/DM；Oracle/SQLServer 方言由 executor 层适配）。
     * </p>
     */
    private CompiledSql applyQueryOptions(CompiledSql compiled, JSONObject req) {
        String sql = compiled.sql();
        List<SqlParam> params = new ArrayList<SqlParam>(compiled.params());

        // GROUP BY
        JSONArray groupByArr = req.getJSONArray("groupBy");
        if (groupByArr != null && !groupByArr.isEmpty()) {
            StringBuilder gb = new StringBuilder(" GROUP BY ");
            for (int i = 0; i < groupByArr.size(); i++) {
                if (i > 0) {
                    gb.append(", ");
                }
                gb.append(groupByArr.getString(i));
            }
            sql = sql + gb.toString();
        }

        // ORDER BY
        JSONArray orderByArr = req.getJSONArray("orderBy");
        if (orderByArr != null && !orderByArr.isEmpty()) {
            StringBuilder ob = new StringBuilder(" ORDER BY ");
            for (int i = 0; i < orderByArr.size(); i++) {
                if (i > 0) {
                    ob.append(", ");
                }
                JSONObject o = orderByArr.getJSONObject(i);
                ob.append(o.getString("column"));
                String dir = o.getString("direction");
                if (dir != null && dir.equalsIgnoreCase("desc")) {
                    ob.append(" DESC");
                } else {
                    ob.append(" ASC");
                }
            }
            sql = sql + ob.toString();
        }

        // 分页
        Integer page = req.getInteger("page");
        Integer pageSize = req.getInteger("pageSize");
        if (page != null && pageSize != null) {
            int offset = (page - 1) * pageSize;
            sql = sql + " LIMIT " + pageSize + " OFFSET " + offset;
        } else if (pageSize != null) {
            sql = sql + " LIMIT " + pageSize;
        }

        return new CompiledSql(sql, params);
    }

    /**
     * JSON 驱动批量查询 —— 多条 SELECT 在同一连接执行，返回多结果集。
     *
     * <p>返回 JSONObject，key = tableName + alias（alias 为空则仅 tableName，
     * tableName 为空则用索引 i），value 为该查询结果集 JSONArray。</p>
     *
     * <p>请求体示例：</p>
     * <pre>{@code
     * {
     * "queries": [
     * {"tableName": "users", "conditions": [...]},
     * {"tableName": "orders", "conditions": [...]}
     * ]
     * }
     * }</pre>
     */
    public ResMsg batchQuery(JSONObject req, @Nullable String dbName) {
        try {
            JSONArray queries = req.getJSONArray("queries");
            if (queries == null || queries.isEmpty()) {
                throw new IllegalArgumentException("queries 不能为空");
            }

            List<CompiledSql> sqlList = new ArrayList<CompiledSql>();
            List<String> keyList = new ArrayList<String>();
            for (int i = 0; i < queries.size(); i++) {
                JSONObject q = queries.getJSONObject(i);

                sqlList.add(compileQuery(q, dbName));

                String tableName = q.getString("tableName");
                String alias = q.getString("alias");
                if (alias == null || alias.isEmpty()) {
                    JSONArray conditions = q.getJSONArray("conditions");
                    alias = converter.extractAlias(conditions);
                }
                String key = (tableName == null || tableName.isEmpty())
                        ? String.valueOf(i)
                        : tableName + (alias != null ? alias : "");
                keyList.add(key);
            }

            log.debug("SQL引擎-批量查询: {} 条", sqlList.size());
            List<JSONArray> results = executor.batchQuery(dbName, sqlList);

            JSONObject data = new JSONObject();
            for (int i = 0; i < results.size(); i++) {
                data.put(keyList.get(i), results.get(i));
            }
            ResMsg r = new ResMsg();
            r.setData(data);
            return r;
        } catch (Exception e) {
            log.error("SQL引擎-批量查询失败", e);
            ResMsg r = new ResMsg();
            r.setRes(false);
            r.setMsg(e.getMessage());
            return r;
        }
    }

    /**
     * JSON 驱动查询总行数（配合分页使用）。
     */
    public ResMsg count(JSONObject req, @Nullable String dbName) {
        try {
            String tableName = req.getString("tableName");
            if (tableName == null || tableName.isEmpty()) {
                throw new IllegalArgumentException("tableName 不能为空");
            }

            JSONArray conditions = req.getJSONArray("conditions");

            List<String> conditionCols = converter.extractColumnNames(conditions);
            validator.validateTableAndColumns(dbName, tableName, conditionCols);

            String alias = converter.extractAlias(conditions);
            SelectQuery.Builder builder = (alias != null && !alias.isEmpty())
                    ? SelectQuery.builder().from(tableName, alias)
                    : SelectQuery.builder().from(tableName);
            builder.column("COUNT(*)");

            Condition where = converter.convert(conditions);
            if (where != null) {
                builder.where(where);
            }

            CompiledSql compiled = sqlEngine.select(builder.build(), dbName);
            log.debug("SQL引擎-计数: {}", compiled.sql());

            long total = executor.count(dbName, compiled);
            ResMsg r = new ResMsg();
            r.setData(total);
            return r;
        } catch (Exception e) {
            log.error("SQL引擎-计数失败", e);
            ResMsg r = new ResMsg();
            r.setRes(false);
            r.setMsg(e.getMessage());
            return r;
        }
    }

    // ==================== 新增 ====================

    /**
     * JSON 驱动新增 —— 接受前端 JSONObject，编译为参数化 INSERT 并执行。
     */
    public ResMsg insert(JSONObject req, @Nullable String dbName) {
        try {
            String tableName = req.getString("tableName");
            if (tableName == null || tableName.isEmpty()) {
                throw new IllegalArgumentException("tableName 不能为空");
            }

            JSONObject data = req.getJSONObject("data");
            if (data == null || data.isEmpty()) {
                throw new IllegalArgumentException("data 不能为空");
            }

            validator.validateSetClauses(dbName, tableName, data);

            InsertStatement.Builder builder = InsertStatement.builder().into(tableName);
            for (String key : data.keySet()) {
                builder.set(key, data.get(key));
            }

            JSONArray skipCols = req.getJSONArray("skipColumns");
            if (skipCols != null) {
                for (int i = 0; i < skipCols.size(); i++) {
                    builder.skip(skipCols.getString(i));
                }
            }

            JSONArray timestampCols = req.getJSONArray("autoTimestampColumns");
            if (timestampCols != null) {
                com.server.sqlengine.dialect.SqlDialect dialect = compiler.getDialect(dbName);
                String tsLiteral = dialect.currentTimestamp();
                for (int i = 0; i < timestampCols.size(); i++) {
                    String col = timestampCols.getString(i);
                    builder.setExpr(col, tsLiteral);
                }
            }

            JSONObject seqCols = req.getJSONObject("sequenceColumns");
            if (seqCols != null) {
                com.server.sqlengine.dialect.SqlDialect dialect = compiler.getDialect(dbName);
                for (String col : seqCols.keySet()) {
                    String seqName = seqCols.getString(col);
                    builder.setExpr(col, dialect.sequenceNextVal(seqName));
                }
            }

            JSONObject rawExprs = req.getJSONObject("rawExpressions");
            if (rawExprs != null) {
                for (String col : rawExprs.keySet()) {
                    builder.setExpr(col, rawExprs.getString(col));
                }
            }

            CompiledSql compiled = sqlEngine.insert(builder.build(), dbName);
            log.debug("SQL引擎-新增: {}", compiled.sql());

            int rows = executor.update(dbName, compiled);
            ResMsg r = new ResMsg();
            r.setData(rows);
            return r;
        } catch (Exception e) {
            log.error("SQL引擎-新增失败", e);
            ResMsg r = new ResMsg();
            r.setRes(false);
            r.setMsg(e.getMessage());
            return r;
        }
    }

    /**
     * JSON 驱动批量新增 —— data 为 JSONArray，每个元素是一行数据。
     */
    public ResMsg batchInsert(JSONObject req, @Nullable String dbName) {
        try {
            String tableName = req.getString("tableName");
            if (tableName == null || tableName.isEmpty()) {
                throw new IllegalArgumentException("tableName 不能为空");
            }

            JSONArray rows = req.getJSONArray("data");
            if (rows == null || rows.isEmpty()) {
                throw new IllegalArgumentException("data 不能为空");
            }

            InsertStatement.Builder builder = InsertStatement.builder().into(tableName);
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                Map<String, Object> rowMap = new LinkedHashMap<String, Object>();
                for (String key : row.keySet()) {
                    rowMap.put(key, row.get(key));
                }
                builder.addBatchRow(rowMap);
            }

            InsertStatement stmt = builder.build();
            CompiledSql compiled = sqlEngine.insert(stmt, dbName);
            log.debug("SQL引擎-批量新增: {}", compiled.sql());

            List<List<SqlParam>> batchParams = new ArrayList<List<SqlParam>>();
            for (int i = 0; i < rows.size(); i++) {
                batchParams.add(compiler.compileInsertBatchRow(stmt, i, dbName));
            }

            int[] result = executor.batch(dbName, compiled, batchParams);
            ResMsg r = new ResMsg();
            r.setData(result);
            return r;
        } catch (Exception e) {
            log.error("SQL引擎-批量新增失败", e);
            ResMsg r = new ResMsg();
            r.setRes(false);
            r.setMsg(e.getMessage());
            return r;
        }
    }

    // ==================== 修改 ====================

    /**
     * JSON 驱动修改 —— 接受前端 JSONObject，编译为参数化 UPDATE 并执行。
     */
    public ResMsg update(JSONObject req, @Nullable String dbName) {
        try {
            String tableName = req.getString("tableName");
            if (tableName == null || tableName.isEmpty()) {
                throw new IllegalArgumentException("tableName 不能为空");
            }

            JSONObject data = req.getJSONObject("data");
            if (data == null || data.isEmpty()) {
                throw new IllegalArgumentException("data 不能为空");
            }

            JSONArray conditions = req.getJSONArray("conditions");
            Condition where = converter.convert(conditions);
            if (where == null) {
                throw new IllegalArgumentException("UPDATE 操作必须提供 conditions 条件");
            }

            List<String> conditionCols = converter.extractColumnNames(conditions);
            List<String> allCols = new ArrayList<String>(data.keySet());
            allCols.addAll(conditionCols);
            validator.validateTableAndColumns(dbName, tableName, allCols);

            String alias = converter.extractAlias(conditions);
            UpdateStatement.Builder builder = (alias != null && !alias.isEmpty())
                    ? UpdateStatement.builder().table(tableName, alias)
                    : UpdateStatement.builder().table(tableName);
            for (String key : data.keySet()) {
                builder.set(key, data.get(key));
            }

            JSONArray timestampCols = req.getJSONArray("autoTimestampColumns");
            if (timestampCols != null) {
                com.server.sqlengine.dialect.SqlDialect dialect = compiler.getDialect(dbName);
                String tsLiteral = dialect.currentTimestamp();
                for (int i = 0; i < timestampCols.size(); i++) {
                    String col = timestampCols.getString(i);
                    builder.setExpr(col, tsLiteral);
                }
            }

            JSONObject rawExprs = req.getJSONObject("rawExpressions");
            if (rawExprs != null) {
                for (String col : rawExprs.keySet()) {
                    builder.setExpr(col, rawExprs.getString(col));
                }
            }

            JSONObject increments = req.getJSONObject("increments");
            if (increments != null) {
                for (String col : increments.keySet()) {
                    builder.setIncrement(col, increments.getBigDecimal(col));
                }
            }

            builder.where(where);

            CompiledSql compiled = sqlEngine.update(builder.build(), dbName);
            log.debug("SQL引擎-修改: {}", compiled.sql());

            int rows = executor.update(dbName, compiled);
            ResMsg r = new ResMsg();
            r.setData(rows);
            return r;
        } catch (Exception e) {
            log.error("SQL引擎-修改失败", e);
            ResMsg r = new ResMsg();
            r.setRes(false);
            r.setMsg(e.getMessage());
            return r;
        }
    }

    /**
     * JSON 驱动批量修改 —— data 为 JSONArray，每个元素含 data + conditions。
     *
     * <p>请求体示例：</p>
     * <pre>{@code
     * {
     * "tableName": "users",
     * "batch": [
     * {"data": {"status": "active"}, "conditions": [{"Id": "id", "Symbol": "=",
     * "Val": "1", "TableAlias": "T"}]},
     * {"data": {"status": "active"}, "conditions": [{"Id": "id", "Symbol": "=",
     * "Val": "2", "TableAlias": "T"}]}
     * ]
     * }
     * }</pre>
     */
    public ResMsg batchUpdate(JSONObject req, @Nullable String dbName) {
        try {
            String tableName = req.getString("tableName");
            if (tableName == null || tableName.isEmpty()) {
                throw new IllegalArgumentException("tableName 不能为空");
            }

            JSONArray batch = req.getJSONArray("batch");
            if (batch == null || batch.isEmpty()) {
                throw new IllegalArgumentException("batch 不能为空");
            }

            List<CompiledSql> sqlList = new ArrayList<CompiledSql>();
            for (int i = 0; i < batch.size(); i++) {
                JSONObject item = batch.getJSONObject(i);
                JSONObject data = item.getJSONObject("data");
                if (data == null || data.isEmpty()) {
                    throw new IllegalArgumentException("batch[" + i + "].data 不能为空");
                }
                JSONArray conditions = item.getJSONArray("conditions");
                Condition where = converter.convert(conditions);
                if (where == null) {
                    throw new IllegalArgumentException("batch[" + i + "].conditions 不能为空");
                }

                UpdateStatement.Builder builder;
                String alias = converter.extractAlias(conditions);
                if (alias != null && !alias.isEmpty()) {
                    builder = UpdateStatement.builder().table(tableName, alias);
                } else {
                    builder = UpdateStatement.builder().table(tableName);
                }
                for (String key : data.keySet()) {
                    builder.set(key, data.get(key));
                }
                builder.where(where);
                sqlList.add(sqlEngine.update(builder.build(), dbName));
            }

            log.debug("SQL引擎-批量修改: {} 条", sqlList.size());
            executor.executeInTransaction(dbName, sqlList);
            int[] result = new int[sqlList.size()];
            java.util.Arrays.fill(result, 1);
            ResMsg r = new ResMsg();
            r.setData(result);
            return r;
        } catch (Exception e) {
            log.error("SQL引擎-批量修改失败", e);
            ResMsg r = new ResMsg();
            r.setRes(false);
            r.setMsg(e.getMessage());
            return r;
        }
    }

    /**
     * JSON 驱动批量删除 —— batch 为 JSONArray，每个元素含独立的 conditions。
     *
     * <p>请求体示例：</p>
     * <pre>{@code
     * {
     * "tableName": "users",
     * "batch": [
     * {"conditions": [{"Id": "id", "Symbol": "=", "Val": "1", "TableAlias": "T"}]},
     * {"conditions": [{"Id": "id", "Symbol": "=", "Val": "2", "TableAlias": "T"}]}
     * ]
     * }
     * }</pre>
     */
    public ResMsg batchDelete(JSONObject req, @Nullable String dbName) {
        try {
            String tableName = req.getString("tableName");
            if (tableName == null || tableName.isEmpty()) {
                throw new IllegalArgumentException("tableName 不能为空");
            }

            JSONArray batch = req.getJSONArray("batch");
            if (batch == null || batch.isEmpty()) {
                throw new IllegalArgumentException("batch 不能为空");
            }

            List<CompiledSql> sqlList = new ArrayList<CompiledSql>();
            for (int i = 0; i < batch.size(); i++) {
                JSONObject item = batch.getJSONObject(i);
                JSONArray conditions = item.getJSONArray("conditions");
                Condition where = converter.convert(conditions);
                if (where == null) {
                    throw new IllegalArgumentException("batch[" + i + "].conditions 不能为空");
                }

                String delAlias = converter.extractAlias(conditions);
                DeleteStatement.Builder builder = DeleteStatement.builder()
                        .from(tableName, delAlias)
                        .where(where);
                sqlList.add(sqlEngine.delete(builder.build(), dbName));
            }

            log.debug("SQL引擎-批量删除: {} 条", sqlList.size());
            executor.executeInTransaction(dbName, sqlList);
            int[] result = new int[sqlList.size()];
            java.util.Arrays.fill(result, 1);
            ResMsg r = new ResMsg();
            r.setData(result);
            return r;
        } catch (Exception e) {
            log.error("SQL引擎-批量删除失败", e);
            ResMsg r = new ResMsg();
            r.setRes(false);
            r.setMsg(e.getMessage());
            return r;
        }
    }

    /**
     * JSON 驱动删除 —— 接受前端 JSONObject，编译为参数化 DELETE 并执行。
     */
    public ResMsg delete(JSONObject req, @Nullable String dbName) {
        try {
            String tableName = req.getString("tableName");
            if (tableName == null || tableName.isEmpty()) {
                throw new IllegalArgumentException("tableName 不能为空");
            }

            JSONArray conditions = req.getJSONArray("conditions");
            Condition where = converter.convert(conditions);
            if (where == null) {
                throw new IllegalArgumentException("DELETE 操作必须提供 conditions 条件");
            }

            List<String> conditionCols = converter.extractColumnNames(conditions);
            validator.validateTableAndColumns(dbName, tableName, conditionCols);

            String alias = converter.extractAlias(conditions);
            DeleteStatement.Builder builder = DeleteStatement.builder()
                    .from(tableName, alias)
                    .where(where);

            CompiledSql compiled = sqlEngine.delete(builder.build(), dbName);
            log.debug("SQL引擎-删除: {}", compiled.sql());

            int rows = executor.update(dbName, compiled);
            ResMsg r = new ResMsg();
            r.setData(rows);
            return r;
        } catch (Exception e) {
            log.error("SQL引擎-删除失败", e);
            ResMsg r = new ResMsg();
            r.setRes(false);
            r.setMsg(e.getMessage());
            return r;
        }
    }

    // ==================== 事务 ====================

    /**
     * 事务批量执行 —— 多条 CompiledSql 在同一事务中执行，全成功才提交。
     */
    public ResMsg executeInTransaction(@Nullable String dbName, List<CompiledSql> sqlList) {
        try {
            executor.executeInTransaction(dbName, sqlList);
            return new ResMsg();
        } catch (Exception e) {
            log.error("SQL引擎-事务执行失败", e);
            ResMsg r = new ResMsg();
            r.setRes(false);
            r.setMsg(e.getMessage());
            return r;
        }
    }
}
