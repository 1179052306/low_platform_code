package com.server.sqlengine.json;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.model.Condition;
import com.server.sqlengine.model.Op;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JSON 条件转换器 —— 将前端 JSONArray 格式的查询条件转为 {@link Condition} 条件树。
 *
 * <p><b>前端 JSON 条件格式</b>（与现有 WhereSql.add 产出一致）：</p>
 * <pre>{@code
 * [
 *   {"Symbol": "and"},
 *   {"Id": "name",  "Symbol": "=",    "Val": "张三", "TableAlias": "T"},
 *   {"Symbol": "or"},
 *   {"Id": "age",   "Symbol": ">",    "Val": "18",   "TableAlias": "T"},
 *   {"Id": "status","Symbol": "like", "Val": "active","TableAlias": "T"}
 * ]
 * }</pre>
 *
 * <p>交替排列：奇数索引为连接符（and/or），偶数索引为条件项。
 * 转换器递归构建 Condition 树，消除旧实现 i%2 平铺解析的脆弱性。</p>
 *
 * @author liwei
 * @date 2026/9/9
 */
@Service
public class JsonConditionConverter {

    /**
     * 从条件 JSON 中提取所有条件引用的列名（去重）。
     * 用于字段合法性校验。
     *
     * @param conditions 前端条件数组
     * @return 列名列表（可能为空，不含 null）
     */
    public List<String> extractColumnNames(@Nullable JSONArray conditions) {
        List<String> columns = new ArrayList<String>();
        if (conditions == null || conditions.isEmpty()) {
            return columns;
        }
        for (int i = 0; i < conditions.size(); i++) {
            JSONObject item = conditions.getJSONObject(i);
            if (item != null && item.containsKey("Id")) {
                String col = item.getString("Id");
                if (col != null && !col.isEmpty()) {
                    // 去掉 alias. 前缀
                    int dot = col.indexOf('.');
                    if (dot > 0) {
                        col = col.substring(dot + 1);
                    }
                    if (!columns.contains(col)) {
                        columns.add(col);
                    }
                }
            }
        }
        return columns;
    }

    /**
     * 从条件 JSON 中提取第一个 TableAlias 值。
     * 用于自动设置 FROM 子句的表别名，使 WHERE 条件中的 alias.column 引用正确。
     *
     * @param conditions 前端条件数组
     * @return 第一个 TableAlias 值，无则 null
     */
    @Nullable
    public String extractAlias(@Nullable JSONArray conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }
        for (int i = 0; i < conditions.size(); i++) {
            JSONObject item = conditions.getJSONObject(i);
            if (item != null && item.containsKey("Id")) {
                String alias = item.getString("TableAlias");
                if (alias != null && !alias.isEmpty()) {
                    return alias.toUpperCase();
                }
            }
        }
        return null;
    }

    /**
     * 将前端 JSONArray 条件转为 Condition 树。
     *
     * @param conditions 前端条件数组（可为 null）
     * @return Condition 树；输入为空时返回 null
     */
    @Nullable
    public Condition convert(@Nullable JSONArray conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }

        List<Condition> leaves = new ArrayList<Condition>();
        List<String> connectors = new ArrayList<String>();

        for (int i = 0; i < conditions.size(); i++) {
            JSONObject item = conditions.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String symbol = item.getString("Symbol");
            // 有 Id 的是条件项，无 Id 的是连接符
            if (item.containsKey("Id")) {
                Condition leaf = parseLeaf(item);
                if (leaf != null) {
                    leaves.add(leaf);
                }
            } else if (symbol != null) {
                connectors.add(symbol.toLowerCase());
            }
        }

        if (leaves.isEmpty()) {
            return null;
        }
        if (leaves.size() == 1) {
            return leaves.get(0);
        }

        // 按连接符组装条件树
        // connectors.size() == leaves.size() - 1（首元素前有隐含 and）
        // 第一个连接符可能不存在（默认 and），后续连接符对应 leaves[i] 和 leaves[i+1] 的连接关系
        return buildTree(leaves, connectors);
    }

    private Condition buildTree(List<Condition> leaves, List<String> connectors) {
        Condition result = leaves.get(0);
        // 连接符在原数组中位于各条件项之前：[connector0, cond0, connector1, cond1, ...]
        // connector0 是首项前的（默认 and），connector_i 连接 cond[i-1] 和 cond[i]
        // 若首项就是条件（无前置 connector），则 connectors[i-1] 是 cond[i] 前的连接符
        boolean hasInitialConnector = connectors.size() == leaves.size();
        for (int i = 1; i < leaves.size(); i++) {
            String connector;
            int connIdx = hasInitialConnector ? i : (i - 1);
            if (connIdx < connectors.size()) {
                connector = connectors.get(connIdx);
            } else {
                connector = "and";
            }
            if (connector == null || connector.isEmpty()) {
                connector = "and";
            }
            if ("or".equals(connector)) {
                result = Condition.or(result, leaves.get(i));
            } else {
                result = Condition.and(result, leaves.get(i));
            }
        }
        return result;
    }

    @Nullable
    private Condition parseLeaf(JSONObject item) {
        String column = item.getString("Id");
        String symbol = item.getString("Symbol");
        Object rawValue = item.get("Val");
        String alias = item.getString("TableAlias");

        if (column == null || column.isEmpty()) {
            return null;
        }

        // 带别名的列名（大写化，与旧元数据约定一致）
        String qualifiedColumn = (alias != null && !alias.isEmpty())
                ? alias.toUpperCase() + "." + column.toUpperCase() : column.toUpperCase();

        if (symbol == null || symbol.isEmpty()) {
            symbol = "=";
        }
        symbol = symbol.trim().toLowerCase();

        // 空值且不是 is null 判断 → 跳过该条件
        if (rawValue == null) {
            if (!symbol.contains("null")) {
                return null;
            }
        } else if (rawValue instanceof String) {
            String strVal = (String) rawValue;
            if (strVal.isEmpty() && !symbol.contains("null")) {
                return null;
            }
        }

        Op op = parseOp(symbol);
        if (op == null) {
            // 不识别的符号，按等值处理（兼容旧逻辑）
            return Condition.eq(qualifiedColumn, rawValue);
        }

        switch (op) {
            case EQ:
                return Condition.eq(qualifiedColumn, rawValue);
            case NE:
                return Condition.ne(qualifiedColumn, rawValue);
            case LT:
                return Condition.lt(qualifiedColumn, rawValue);
            case LE:
                return Condition.le(qualifiedColumn, rawValue);
            case GT:
                return Condition.gt(qualifiedColumn, rawValue);
            case GE:
                return Condition.ge(qualifiedColumn, rawValue);
            case LIKE:
                return Condition.like(qualifiedColumn, "%" + rawValue + "%");
            case NOT_LIKE:
                return Condition.notLike(qualifiedColumn, "%" + rawValue + "%");
            case IS_NULL:
                return Condition.isNull(qualifiedColumn);
            case IS_NOT_NULL:
                return Condition.isNotNull(qualifiedColumn);
            case IN:
                return parseInCondition(qualifiedColumn, rawValue, false);
            case NOT_IN:
                return parseInCondition(qualifiedColumn, rawValue, true);
            case BETWEEN:
                return parseBetweenCondition(qualifiedColumn, rawValue);
            default:
                return Condition.eq(qualifiedColumn, rawValue);
        }
    }

    @Nullable
    private Op parseOp(String symbol) {
        switch (symbol) {
            case "=": case "==": case "eq": return Op.EQ;
            case "<>": case "!=": case "ne": return Op.NE;
            case "<": case "lt": return Op.LT;
            case "<=": case "le": return Op.LE;
            case ">": case "gt": return Op.GT;
            case ">=": case "ge": return Op.GE;
            case "like": return Op.LIKE;
            case "not like": case "notlike": return Op.NOT_LIKE;
            case "is null": case "isnull": case "null": return Op.IS_NULL;
            case "is not null": case "isnotnull": case "notnull": return Op.IS_NOT_NULL;
            case "in": return Op.IN;
            case "not in": case "notin": return Op.NOT_IN;
            case "between": return Op.BETWEEN;
            default: return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Condition parseInCondition(String column, Object value, boolean negate) {
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
        return negate ? Condition.notIn(column, values) : Condition.in(column, values);
    }

    private Condition parseBetweenCondition(String column, Object value) {
        String strVal = String.valueOf(value);
        String[] parts = strVal.split(",");
        if (parts.length < 2) {
            return Condition.eq(column, value);
        }
        Object low = tryParseNumber(parts[0].trim());
        Object high = tryParseNumber(parts[1].trim());
        return Condition.between(column, low, high);
    }

    /**
     * 尝试将字符串解析为数字，失败则返回原始字符串。
     * PostgreSQL 不会自动将字符串参数转 integer 列，需要前端传入正确类型。
     */
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
}
