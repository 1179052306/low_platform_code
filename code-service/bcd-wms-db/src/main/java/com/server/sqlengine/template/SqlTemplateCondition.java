package com.server.sqlengine.template;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SQL 模板条件（对应旧 {@code Rps}）。
 *
 * <p>当 SQL 模板中有 {@code @{field}@} 占位符时，用此对象的 {@code sqlTemplate} 替换。</p>
 *
 * <p>{@code sqlTemplate} 支持 MessageFormat 风格的占位符：</p>
 * <ul>
 *   <li>{0} = 操作符（如 =、&gt;、LIKE）</li>
 *   <li>{1} = 带别名的列名（如 T.CODE）</li>
 * </ul>
 *
 * <p><b>示例</b>：</p>
 * <pre>{@code
 * // 模板: "{1} IS NOT NULL AND {1} {0} ?"
 * // 前端传: {field="status", symbol="IN", value="active,pending"}
 * // 替换后: T.STATUS IS NOT NULL AND T.STATUS IN ?
 * }</pre>
 *
 * @author liwei
 * @date 2026/9/9
 */
public final class SqlTemplateCondition {

    private final String fieldName;
    private final String sqlTemplate;

    public SqlTemplateCondition(String fieldName, String sqlTemplate) {
        this.fieldName = fieldName;
        this.sqlTemplate = sqlTemplate;
    }

    public String fieldName() {
        return fieldName;
    }

    public String sqlTemplate() {
        return sqlTemplate;
    }

    /**
     * 用操作符和列名填充模板。
     *
     * @param symbol       操作符（=、>、LIKE 等）
     * @param qualifiedCol 带别名的列名（如 T.CODE）
     * @return 填充后的 SQL 片段
     */
    public String fill(String symbol, String qualifiedCol) {
        String result = sqlTemplate;
        if (result.contains("{0}")) {
            result = result.replace("{0}", symbol);
        }
        if (result.contains("{1}")) {
            result = result.replace("{1}", qualifiedCol);
        }
        return result;
    }
}
