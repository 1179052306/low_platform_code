package com.server.sqlengine.template;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 模板占位符解析器。
 *
 * <p>解析自定义 SQL 模板中的占位符，支持两种格式：</p>
 * <ul>
 *   <li><b>Rpc 占位符</b>：{@code @{alias.field}@} — 条件注入点，
 *       前端传来的同名列值会替换为 {@code AND alias.field = ?}</li>
 *   <li><b>Rps 模板占位符</b>：{@code @{field}@} — SQL 模板注入点，
 *       匹配 Rps 模板中的 SQL 片段</li>
 * </ul>
 *
 * <p>替代旧 {@code DbParmentReplace.getParamenters()} 的 split 解析方式，
 * 使用正则表达式更精确地匹配占位符。</p>
 *
 * <p><b>示例</b>：</p>
 * <pre>{@code
 * String template = "SELECT * FROM users T WHERE 1=1 @{T.name}@ AND @{T.status}@";
 * List<Placeholder> placeholders = SqlTemplateParser.parse(template);
 * // → [Placeholder(alias="T", field="name"), Placeholder(alias="T", field="status")]
 * }</pre>
 *
 * @author liwei
 * @date 2026/9/9
 */
public final class SqlTemplateParser {

    /**
     * 占位符正则：@{alias.field}@ 或 @{field}@
     */
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("@\\{([^}]+)\\}@");

    private SqlTemplateParser() {
    }

    /**
     * 解析 SQL 模板中的所有占位符。
     *
     * @param sqlTemplate SQL 模板文本
     * @return 占位符列表（可能为空，不含 null）
     */
    public static java.util.List<Placeholder> parse(String sqlTemplate) {
        java.util.List<Placeholder> list = new java.util.ArrayList<Placeholder>();
        if (sqlTemplate == null || sqlTemplate.isEmpty()) {
            return list;
        }
        Matcher m = PLACEHOLDER_PATTERN.matcher(sqlTemplate);
        while (m.find()) {
            String content = m.group(1).trim();
            int dot = content.indexOf('.');
            String alias;
            String field;
            if (dot > 0) {
                alias = content.substring(0, dot).trim();
                field = content.substring(dot + 1).trim();
            } else {
                alias = "";
                field = content;
            }
            list.add(new Placeholder(alias, field, m.start(), m.end()));
        }
        return list;
    }

    /**
     * 构造占位符字符串。
     *
     * @param alias 表别名（可为空）
     * @param field 字段名
     * @return 占位符字符串，如 @{T.name}@
     */
    public static String buildPlaceholder(String alias, String field) {
        if (alias == null || alias.isEmpty()) {
            return "@{" + field + "}@";
        }
        return "@{" + alias + "." + field + "}@";
    }

    /**
     * 抹除未命中的占位符（替换为空字符串）。
     *
     * @param sql          SQL 文本
     * @param alias        表别名
     * @param field        字段名
     * @return 抹除后的 SQL
     */
    public static String removePlaceholder(String sql, String alias, String field) {
        return sql.replace(buildPlaceholder(alias, field), "");
    }

    /**
     * 占位符数据结构。
     */
    public static final class Placeholder {
        private final String alias;
        private final String field;
        private final int startPos;
        private final int endPos;

        Placeholder(String alias, String field, int startPos, int endPos) {
            this.alias = alias;
            this.field = field;
            this.startPos = startPos;
            this.endPos = endPos;
        }

        public String alias() {
            return alias;
        }

        public String field() {
            return field;
        }

        public int startPos() {
            return startPos;
        }

        public int endPos() {
            return endPos;
        }

        public String placeholderText() {
            return buildPlaceholder(alias, field);
        }

        @Override
        public String toString() {
            return "Placeholder{alias='" + alias + "', field='" + field + "'}";
        }
    }
}
