package com.server.sqlengine.template;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.model.Condition;
import com.server.sqlengine.model.Op;
import com.server.sqlengine.model.SqlParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL 模板替换引擎 —— 将含占位符的 SQL 模板与前端条件动态合并。
 *
 * <p>
 * 替代旧 {@code Common.sqlQueryParameter()} + {@code DbParameterSet.setStrSql()}
 * 的三路发射机制，
 * 使用更直观的流程：
 * </p>
 *
 * <ol>
 * <li><b>解析占位符</b>：{@link SqlTemplateParser#parse(String)} 提取所有 @{alias.field}@
 * 占位符</li>
 * <li><b>匹配条件</b>：遍历占位符，在前端条件 JSON 中查找同名字段</li>
 * <li><b>替换</b>：命中 → 替换为 {@code AND alias.field op ?}（参数化）；未命中 → 抹除占位符</li>
 * <li><b>追加剩余</b>：未匹配占位符的条件追加到 SQL 末尾</li>
 * </ol>
 *
 * <p>
 * <b>Rps 模板条件</b>：独立于占位符，遍历 rpsTemplates，命中前端条件时
 * 用其 sqlTemplate 发射并追加到 SQL 末尾（与旧 Common ② Rps 语义对齐）。
 * </p>
 *
 * <p>
 * <b>示例</b>：
 * </p>
 * 
 * <pre>{@code
 * // SQL 模板:
 * // SELECT * FROM users T WHERE 1=1 @{T.name}@ AND @{T.status}@
 * //
 * // 前端条件:
 * // [{"Symbol":"and"},{"Id":"name","Symbol":"like","Val":"张","TableAlias":"T"}]
 * //
 * // 替换结果:
 * // SELECT * FROM users T WHERE 1=1 AND T.NAME LIKE ? AND T.STATUS = ?
 * // 参数: ["%张%"]
 * // (status 未命中条件，但 @{T.status}@ 会被抹除为空)
 * }</pre>
 *
 * @author liwei
 * @date 2026/9/9
 */
@Service
public class SqlTemplateEngine {

    /**
     * 将前端条件注入 SQL 模板。
     *
     * @param sqlTemplate  含 @{alias.field}@ 占位符的 SQL 模板
     * @param conditions   前端条件 JSON 数组（可为 null）
     * @param rpsTemplates Rps 模板条件映射（fieldName → SqlTemplateCondition，可为 null）
     * @param defaultAlias 默认表别名（用于无 alias 的占位符，如 "T"）
     * @return 替换后的 SQL 文本 + 参数列表
     */
    public TemplateResult render(String sqlTemplate,
            @Nullable JSONArray conditions,
            @Nullable Map<String, SqlTemplateCondition> rpsTemplates,
            @Nullable String defaultAlias) {
        String sql = sqlTemplate;
        List<SqlParam> params = new ArrayList<SqlParam>();

        // 1. 解析占位符
        List<SqlTemplateParser.Placeholder> placeholders = SqlTemplateParser.parse(sql);

        // 2. 构建条件索引：fieldName → 条件项（用于快速匹配占位符）
        Map<String, JSONObject> condIndex = buildConditionIndex(conditions);

        // 3. 记录已匹配的条件（避免重复追加）
        java.util.Set<String> matchedFields = new java.util.HashSet<String>();

        // 4. Rpc 占位符：遍历占位符，命中条件 → 替换为 AND alias.field op ?；未命中 → 抹除
        for (SqlTemplateParser.Placeholder ph : placeholders) {
            String placeholderText = ph.placeholderText();
            String fieldUpper = ph.field().toUpperCase();
            JSONObject matched = condIndex.get(fieldUpper);

            if (matched != null) {
                matchedFields.add(fieldUpper);

                String symbol = matched.getString("Symbol");
                Object value = matched.get("Val");
                String alias = ph.alias().isEmpty()
                        ? (defaultAlias != null ? defaultAlias : "")
                        : ph.alias().toUpperCase();
                String qualifiedCol = alias.isEmpty()
                        ? fieldUpper
                        : alias + "." + fieldUpper;

                if (symbol == null || symbol.isEmpty()) {
                    symbol = "=";
                }
                symbol = symbol.trim().toLowerCase();

                Op op = parseOp(symbol);
                String conditionSql = buildConditionSql(qualifiedCol, op, value, params);
                sql = sql.replace(placeholderText, conditionSql);
            } else {
                // 未命中条件：抹除占位符
                sql = sql.replace(placeholderText, "");
            }
        }

        // 5. Rps 模板条件：独立于占位符，遍历 rpsTemplates，命中条件 → 用模板发射并追加
        StringBuilder tail = new StringBuilder();
        if (rpsTemplates != null) {
            for (Map.Entry<String, SqlTemplateCondition> entry : rpsTemplates.entrySet()) {
                String fieldUpper = entry.getKey().toUpperCase();
                if (matchedFields.contains(fieldUpper)) {
                    continue;
                }
                JSONObject matched = condIndex.get(fieldUpper);
                if (matched == null) {
                    continue;
                }
                matchedFields.add(fieldUpper);

                String symbol = matched.getString("Symbol");
                Object value = matched.get("Val");
                String alias = matched.getString("TableAlias");
                if (alias == null || alias.isEmpty()) {
                    alias = defaultAlias != null ? defaultAlias : "";
                }
                alias = alias.toUpperCase();
                String qualifiedCol = alias.isEmpty()
                        ? fieldUpper
                        : alias + "." + fieldUpper;

                if (symbol == null || symbol.isEmpty()) {
                    symbol = "=";
                }

                SqlTemplateCondition rps = entry.getValue();
                String sqlFragment = rps.fill(symbol.toUpperCase(), qualifiedCol);
                if (sqlFragment.contains("?") && value != null) {
                    params.add(new SqlParam(fieldUpper, value));
                }
                tail.append(" AND ").append(sqlFragment);
            }
        }

        // 6. 其余条件按原序追加（未被 Rpc/Rps 消费的）
        if (conditions != null) {
            for (int i = 0; i < conditions.size(); i++) {
                JSONObject item = conditions.getJSONObject(i);
                if (item == null || !item.containsKey("Id")) {
                    continue;
                }
                String field = item.getString("Id");
                String fieldUpper = field.toUpperCase();
                if (matchedFields.contains(fieldUpper)) {
                    continue;
                }

                String symbol = item.getString("Symbol");
                Object value = item.get("Val");
                String alias = item.getString("TableAlias");
                if (alias == null || alias.isEmpty()) {
                    alias = defaultAlias != null ? defaultAlias : "";
                }
                alias = alias.toUpperCase();
                String qualifiedCol = alias.isEmpty()
                        ? fieldUpper
                        : alias + "." + fieldUpper;

                if (symbol == null || symbol.isEmpty()) {
                    symbol = "=";
                }
                symbol = symbol.trim().toLowerCase();

                // 空值跳过（非 is null 场景）
                if (value == null && !symbol.contains("null")) {
                    continue;
                }

                Op op = parseOp(symbol);
                String conditionSql = buildConditionSql(qualifiedCol, op, value, params);
                tail.append(conditionSql);
            }
        }
        sql = sql + tail.toString();

        return new TemplateResult(sql, params);
    }

    /**
     * 构建条件索引：fieldName(大写) → JSONObject
     */
    private Map<String, JSONObject> buildConditionIndex(@Nullable JSONArray conditions) {
        Map<String, JSONObject> index = new LinkedHashMap<String, JSONObject>();
        if (conditions == null) {
            return index;
        }
        for (int i = 0; i < conditions.size(); i++) {
            JSONObject item = conditions.getJSONObject(i);
            if (item != null && item.containsKey("Id")) {
                String field = item.getString("Id");
                if (field != null && !field.isEmpty()) {
                    // 去掉 alias. 前缀
                    int dot = field.indexOf('.');
                    String bareField = dot > 0 ? field.substring(dot + 1) : field;
                    index.putIfAbsent(bareField.toUpperCase(), item);
                }
            }
        }
        return index;
    }

    /**
     * 构建单个条件的 SQL 片段并收集参数。
     */
    private String buildConditionSql(String qualifiedCol, Op op,
            @Nullable Object value, List<SqlParam> params) {
        String colName = qualifiedCol.contains(".")
                ? qualifiedCol.substring(qualifiedCol.indexOf('.') + 1)
                : qualifiedCol;
        if (op == null) {
            params.add(new SqlParam(colName, value));
            return " AND " + qualifiedCol + " = ?";
        }

        switch (op) {
            case EQ:
                params.add(new SqlParam(colName, value));
                return " AND " + qualifiedCol + " = ?";
            case NE:
                params.add(new SqlParam(colName, value));
                return " AND " + qualifiedCol + " <> ?";
            case LT:
                params.add(new SqlParam(colName, value));
                return " AND " + qualifiedCol + " < ?";
            case LE:
                params.add(new SqlParam(colName, value));
                return " AND " + qualifiedCol + " <= ?";
            case GT:
                params.add(new SqlParam(colName, value));
                return " AND " + qualifiedCol + " > ?";
            case GE:
                params.add(new SqlParam(colName, value));
                return " AND " + qualifiedCol + " >= ?";
            case LIKE:
                params.add(new SqlParam(colName, "%" + value + "%"));
                return " AND " + qualifiedCol + " LIKE ?";
            case NOT_LIKE:
                params.add(new SqlParam(colName, "%" + value + "%"));
                return " AND " + qualifiedCol + " NOT LIKE ?";
            case IS_NULL:
                return " AND " + qualifiedCol + " IS NULL";
            case IS_NOT_NULL:
                return " AND " + qualifiedCol + " IS NOT NULL";
            case IN:
                return buildInClause(colName, qualifiedCol, value, params, false);
            case NOT_IN:
                return buildInClause(colName, qualifiedCol, value, params, true);
            case BETWEEN:
                return buildBetweenClause(colName, qualifiedCol, value, params);
            default:
                params.add(new SqlParam(colName, value));
                return " AND " + qualifiedCol + " = ?";
        }
    }

    @SuppressWarnings("unchecked")
    private String buildInClause(String colName, String col, Object value, List<SqlParam> params, boolean negate) {
        List<?> values;
        if (value instanceof List) {
            values = (List<?>) value;
        } else if (value instanceof JSONArray) {
            values = ((JSONArray) value).toJavaList(Object.class);
        } else {
            String strVal = String.valueOf(value);
            if (strVal.startsWith("[")) {
                values = JSONArray.parse(strVal).toJavaList(Object.class);
            } else {
                String[] parts = strVal.split(",");
                List<Object> list = new ArrayList<Object>();
                for (String p : parts) {
                    list.add(p.trim());
                }
                values = list;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(" AND ").append(col).append(negate ? " NOT IN (" : " IN (");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('?');
            params.add(new SqlParam(colName, values.get(i)));
        }
        sb.append(')');
        return sb.toString();
    }

    private String buildBetweenClause(String colName, String col, Object value, List<SqlParam> params) {
        String strVal = String.valueOf(value);
        String[] parts = strVal.split(",");
        if (parts.length < 2) {
            params.add(new SqlParam(colName, value));
            return " AND " + col + " = ?";
        }
        Object low = tryParseNumber(parts[0].trim());
        Object high = tryParseNumber(parts[1].trim());
        params.add(new SqlParam(colName, low));
        params.add(new SqlParam(colName, high));
        return " AND " + col + " BETWEEN ? AND ?";
    }

    private Object tryParseNumber(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e1) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e2) {
                try {
                    return Double.parseDouble(str);
                } catch (NumberFormatException e3) {
                    return str;
                }
            }
        }
    }

    @Nullable
    private Op parseOp(String symbol) {
        if (symbol == null) {
            return null;
        }
        switch (symbol) {
            case "=":
            case "==":
            case "eq":
                return Op.EQ;
            case "<>":
            case "!=":
            case "ne":
                return Op.NE;
            case "<":
            case "lt":
                return Op.LT;
            case "<=":
            case "le":
                return Op.LE;
            case ">":
            case "gt":
                return Op.GT;
            case ">=":
            case "ge":
                return Op.GE;
            case "like":
                return Op.LIKE;
            case "not like":
            case "notlike":
                return Op.NOT_LIKE;
            case "is null":
            case "isnull":
            case "null":
                return Op.IS_NULL;
            case "is not null":
            case "isnotnull":
            case "notnull":
                return Op.IS_NOT_NULL;
            case "in":
                return Op.IN;
            case "not in":
            case "notin":
                return Op.NOT_IN;
            case "between":
                return Op.BETWEEN;
            default:
                return null;
        }
    }

    /**
     * 模板替换结果。
     */
    public static final class TemplateResult {
        private final String sql;
        private final List<SqlParam> params;

        TemplateResult(String sql, List<SqlParam> params) {
            this.sql = sql;
            this.params = Collections.unmodifiableList(params);
        }

        public String sql() {
            return sql;
        }

        public List<SqlParam> params() {
            return params;
        }
    }
}
