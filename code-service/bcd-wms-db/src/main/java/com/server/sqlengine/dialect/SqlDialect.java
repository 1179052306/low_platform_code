package com.server.sqlengine.dialect;

/**
 * SQL 方言接口 —— 全新引擎的方言抽象。
 *
 * <p>
 * 每种数据库一个实现，由 {@code SqlDialectRegistry} 按 {@link #dbType()} 自动注册。
 * 方言负责所有 SQL 语法差异：分页、标识符引用、时间函数、序列、数学函数、字符串函数等。
 * </p>
 *
 * <p>
 * 替代旧 {@code com.server.syntax.val.DbValueServer} 接口，覆盖其全部核心方法。
 * </p>
 *
 * <p>
 * <b>新增数据库支持 = 新增一个实现类</b>，无需修改编译器或任何现有方言。
 * </p>
 *
 * @author liwei
 * @date 2026/9/9
 */
public interface SqlDialect {

    // ==================== 基础 ====================

    /**
     * 本方言对应的数据库类型标识，取值见 {@link com.server.db.DbType}。
     */
    String dbType();

    /**
     * 分页 SQL 包装：在原始 SQL 外层包装分页语法。
     *
     * @param sql    已编译的 SQL 文本（不含分页）
     * @param limit  最大行数（不为 null）
     * @param offset 跳过行数（可为 null 表示从0开始）
     * @return 带分页语法的最终 SQL
     */
    String paginate(String sql, int limit, Integer offset);

    /**
     * DELETE 语句是否需要 FROM 关键字。
     * PostgreSQL: {@code DELETE FROM table} ; 其余: {@code DELETE table}
     */
    boolean deleteNeedsFrom();

    /**
     * 引用标识符（表名/列名），处理各库的引号规则。
     * PostgreSQL: "column" ; SQL Server: [column] ; Oracle/DM: 原样
     */
    String quoteIdentifier(String identifier);

    /**
     * 布尔值字面量表示。
     */
    String booleanLiteral(boolean value);

    // ==================== 时间/日期函数 ====================

    /**
     * 数据库当前时间戳字面量（用于 INSERT/UPDATE 中 create_time / update_time 自动填充）。
     *
     * <p>
     * 各方言实现：
     * </p>
     * <ul>
     * <li>PostgreSQL: {@code CURRENT_TIMESTAMP}</li>
     * <li>Oracle: {@code SYSTIMESTAMP}</li>
     * <li>DM: {@code SYSDATE}</li>
     * <li>SQL Server: {@code GETDATE()}</li>
     * </ul>
     */
    String currentTimestamp();

    /**
     * 带格式的数据库当前时间表达式。
     *
     * <p>
     * 各方言实现：
     * </p>
     * <ul>
     * <li>PostgreSQL: {@code to_char(current_timestamp, 'format')}</li>
     * <li>Oracle: {@code to_char(SYSTIMESTAMP, 'format')}</li>
     * <li>DM: {@code to_char(SYSDATE, 'format')}</li>
     * <li>SQL Server: {@code CONVERT(varchar, GETDATE(), format_style)}</li>
     * </ul>
     *
     * @param format 日期格式串（PG/Oracle 风格，如 'yyyy-mm-dd hh24:mi:ss'）
     * @return 带格式的时间表达式
     */
    String sysDate(String format);

    /**
     * 时间字符串转 timestamp 表达式。
     *
     * <p>
     * 各方言实现：
     * </p>
     * <ul>
     * <li>PostgreSQL: {@code to_timestamp('time_str', 'format')::timestamp}</li>
     * <li>Oracle: {@code TO_TIMESTAMP('time_str', 'format')}</li>
     * <li>DM: {@code TO_TIMESTAMP('time_str', 'format')}</li>
     * <li>SQL Server: {@code CONVERT(datetime, 'time_str')}</li>
     * </ul>
     *
     * @param timeStr 时间字符串字面量
     * @param format  日期格式串
     * @return 转换表达式
     */
    String dateToTimestamp(String timeStr, String format);

    /**
     * 日期区间运算表达式（如加 N 天/月/年）。
     *
     * <p>
     * 各方言实现：
     * </p>
     * <ul>
     * <li>PostgreSQL: {@code INTERVAL 'value type'}</li>
     * <li>Oracle: {@code NUMTOYMINTERVAL(value, 'type')} /
     * {@code NUMTODSINTERVAL(value, 'type')}</li>
     * <li>DM: 同 Oracle</li>
     * <li>SQL Server: {@code DATEADD(type, value, base)}</li>
     * </ul>
     *
     * @param value 数量
     * @param type  区间类型（DAY/MONTH/YEAR 等）
     * @return 区间表达式
     */
    String dateInterval(String value, String type);

    /**
     * 日期减法（计算天数差）。
     *
     * <p>
     * 各方言实现：
     * </p>
     * <ul>
     * <li>PostgreSQL: {@code FLOOR(EXTRACT(EPOCH FROM (start - end)) / 86400)}</li>
     * <li>Oracle: {@code TRUNC(start - end)}</li>
     * <li>DM: 同 Oracle</li>
     * <li>SQL Server: {@code DATEDIFF(day, end, start)}</li>
     * </ul>
     *
     * @param startExpr 起始日期表达式
     * @param endExpr   结束日期表达式
     * @return 天数差表达式
     */
    String dateSubtraction(String startExpr, String endExpr);

    // ==================== 序列函数 ====================

    /**
     * 序列取下一个值的 SQL 表达式（用于 INSERT 中主键由序列生成）。
     *
     * <p>
     * 各方言实现：
     * </p>
     * <ul>
     * <li>PostgreSQL: {@code nextval('seq_name')}</li>
     * <li>Oracle: {@code SEQ_NAME.NEXTVAL}</li>
     * <li>DM: {@code SEQ_NAME.NEXTVAL}</li>
     * <li>SQL Server: {@code NEXT VALUE FOR seq_name}</li>
     * </ul>
     *
     * @param sequenceName 序列名
     * @return 取下一个值的 SQL 表达式
     */
    String sequenceNextVal(String sequenceName);

    /**
     * 重置序列起始值的 SQL 语句。
     *
     * <p>
     * 各方言实现：
     * </p>
     * <ul>
     * <li>PostgreSQL: {@code SELECT setval('seq_name', startVal, false)}</li>
     * <li>Oracle: {@code SELECT SETVAL('seq_name', startVal, 0) FROM DUAL}</li>
     * <li>DM: 同 Oracle</li>
     * <li>SQL Server: 不支持（返回 null）</li>
     * </ul>
     *
     * @param sequenceName 序列名
     * @param startVal     起始值
     * @return 重置序列的 SQL 语句
     */
    String resetSequenceSql(String sequenceName, int startVal);

    // ==================== 数学函数 ====================

    /**
     * 向上取整表达式。
     *
     * <p>
     * PostgreSQL/Oracle/DM: {@code CEIL(expr)} ; SQL Server: {@code CEILING(expr)}
     * </p>
     *
     * @param expr 数学表达式
     * @return CEIL 表达式
     */
    String ceil(String expr);

    /**
     * 向下取整表达式。
     *
     * <p>
     * 所有方言: {@code FLOOR(expr)}
     * </p>
     *
     * @param expr 数学表达式
     * @return FLOOR 表达式
     */
    String floor(String expr);

    /**
     * 取模运算表达式。
     *
     * <p>
     * PostgreSQL/SQL Server: {@code expr % divisor} ; Oracle/DM:
     * {@code MOD(expr, divisor)}
     * </p>
     *
     * @param expr    被取模表达式
     * @param divisor 除数
     * @return 取模表达式
     */
    String mod(String expr, String divisor);

    /**
     * 小数精度控制表达式。
     *
     * <p>
     * PostgreSQL: {@code expr::float} ; Oracle/DM: {@code ROUND(expr, precision)} ;
     * SQL Server: {@code ROUND(expr, precision, 0)}
     * </p>
     *
     * @param expr      数值表达式
     * @param precision 小数位数
     * @return 精度控制表达式
     */
    String decimalPoint(String expr, int precision);

    // ==================== 字符串函数 ====================

    /**
     * 字符串连接符。
     *
     * <p>
     * PostgreSQL/Oracle/DM: {@code ||} ; SQL Server: {@code +}
     * </p>
     *
     * @return 连接符字符串
     */
    String concatSymbol();

    /**
     * 去空格表达式。
     *
     * <p>
     * 所有方言: {@code TRIM(expr)}
     * </p>
     *
     * @param expr 字符串表达式
     * @return TRIM 表达式
     */
    String trim(String expr);

    /**
     * 转换为 VARCHAR 的表达式。
     *
     * <p>
     * PostgreSQL: {@code NVL(CAST(expr AS text), '')} ;
     * Oracle/DM: {@code TO_CHAR(expr)} ; SQL Server: {@code CAST(expr AS varchar)}
     * </p>
     *
     * @param expr 表达式
     * @return VARCHAR 转换表达式
     */
    String convertVarchar(String expr);

    // ==================== 行/伪列 ====================

    /**
     * 行限制表达式（用于无分页的简单行数限制）。
     *
     * <p>
     * PostgreSQL: {@code LIMIT row} ; Oracle/DM: {@code AND ROWNUM <= row} ;
     * SQL Server: {@code TOP row}
     * </p>
     *
     * @param rows 最大行数
     * @return 行限制 SQL 片段
     */
    String rowLimit(int rows);

    /**
     * 行号伪列表达式（用于行编号）。
     *
     * <p>
     * PostgreSQL: {@code row_number() over()} ; Oracle/DM: {@code ROWNUM} ;
     * SQL Server: {@code ROW_NUMBER() OVER(...)}
     * </p>
     *
     * @return 行号表达式
     */
    String rowNumber();

    // ==================== 通用/语法 ====================

    /**
     * 无表查询的 SELECT 语法（用于标量查询）。
     *
     * <p>
     * PostgreSQL/SQL Server: {@code SELECT expr} ;
     * Oracle/DM: {@code SELECT expr FROM DUAL}
     * </p>
     *
     * @param expr 查询表达式
     * @return 完整的 SELECT 语句
     */
    String dummySelect(String expr);

    /**
     * SQL 关键字大小写转换（用于统一 SQL 风格）。
     *
     * <p>
     * PostgreSQL: 转小写 ; Oracle/DM: 转大写 ; SQL Server: 原样
     * </p>
     *
     * @param sql SQL 文本
     * @return 转换大小写后的 SQL
     */
    String keywordCase(String sql);

    /**
     * 解析 JDBC executeBatch 返回值，统计实际影响行数。
     *
     * <p>
     * PostgreSQL: 返回值 == 1 计为 1 行 ;
     * Oracle/DM: 返回值 == -2 (SUCCESS_NO_INFO) 也计为 1 行 ;
     * SQL Server: 同 PG
     * </p>
     *
     * @param results JDBC batch 返回值数组
     * @return 实际影响行数
     */
    int batchResult(int[] results);

    /**
     * 建表语法（从已有表结构复制）。
     *
     * <p>
     * PostgreSQL: {@code CREATE TABLE target (LIKE source INCLUDING ALL)} ;
     * Oracle/DM: {@code CREATE TABLE target AS SELECT * FROM source WHERE 1=0} ;
     * SQL Server: {@code SELECT * INTO target FROM source WHERE 1=0}
     * </p>
     *
     * @param targetTable 目标表名
     * @param sourceTable 源表名
     * @return 建表 SQL 语句
     */
    String createTableLike(String targetTable, String sourceTable);

    // ==================== 元数据查询 ====================

    /**
     * 查询表所有列名的参数化 SQL。
     *
     * <p>
     * SQL 使用 {@code ?} 作为表名参数占位符，结果集只有一列（列名）。
     * 调用方通过 {@link SqlExecutor#query} 执行，返回的 JSON key 经大写化后为 {@code COLUMN_NAME}。
     * </p>
     *
     * <p>
     * 各方言实现：
     * </p>
     * <ul>
     * <li>PostgreSQL:
     * {@code SELECT column_name FROM information_schema.columns WHERE table_name = ? AND table_schema = (SELECT current_schema()) ORDER BY ordinal_position}</li>
     * <li>Oracle:
     * {@code SELECT column_name FROM all_tab_columns WHERE table_name = ? AND owner = (SELECT user FROM dual) ORDER BY column_id}</li>
     * <li>DM: 同 Oracle</li>
     * <li>SQL Server:
     * {@code SELECT column_name FROM information_schema.columns WHERE table_name = ? AND table_schema = (SELECT SCHEMA_NAME()) ORDER BY ordinal_position}</li>
     * </ul>
     *
     * @return 参数化 SQL 文本（含一个 {@code ?} 占位符）
     */
    String queryTableColumnsSql();

    /**
     * 检查表是否存在的参数化 SQL。
     *
     * <p>
     * SQL 使用 {@code ?} 作为表名参数占位符，返回至少一行表示表存在，空结果集表示不存在。
     * 相比直接 {@code SELECT 1 FROM table} 拼接表名，查系统表更安全且无需格式校验。
     * </p>
     *
     * <p>
     * 各方言实现：
     * </p>
     * <ul>
     * <li>PostgreSQL:
     * {@code SELECT 1 FROM information_schema.tables WHERE table_name = ? AND table_schema = (SELECT current_schema())}</li>
     * <li>Oracle: {@code SELECT 1 FROM user_tables WHERE table_name = ?}</li>
     * <li>DM: 同 Oracle</li>
     * <li>SQL Server:
     * {@code SELECT 1 FROM information_schema.tables WHERE table_name = ? AND table_schema = (SELECT SCHEMA_NAME())}</li>
     * </ul>
     *
     * @return 参数化 SQL 文本（含一个 {@code ?} 占位符）
     */
    String checkTableExistsSql();

    /**
     * 将标识符转换为系统表中存储的格式。
     *
     * <p>
     * 不同数据库的系统表对标识符大小写处理不同，查询时需匹配存储格式：
     * </p>
     * <ul>
     * <li>PostgreSQL: 不带引号的标识符折叠为小写，{@code information_schema} 存小写</li>
     * <li>Oracle: 标识符默认折叠为大写，{@code user_tables/all_tab_columns} 存大写</li>
     * <li>DM: 同 Oracle</li>
     * <li>SQL Server: 默认排序规则决定，通常不转换</li>
     * </ul>
     *
     * @param identifier 原始标识符（表名/列名）
     * @return 系统表查询时使用的格式
     */
    String normalizeSchemaIdentifier(String identifier);

    /**
     * 查询表结构信息（列名、数据类型、长度、精度、是否可空）的参数化 SQL。
     *
     * <p>
     * SQL 使用 {@code ?} 作为表名参数占位符，结果集统一以下划线大写别名返回：
     * </p>
     * <ul>
     * <li>{@code COLUMN_NAME} — 列名</li>
     * <li>{@code DATA_TYPE} — 数据类型</li>
     * <li>{@code DATA_LENGTH} — 字符最大长度（可为 null）</li>
     * <li>{@code DATA_PRECISION} — 数值精度（可为 null）</li>
     * <li>{@code DATA_SCALE} — 数值标度（可为 null）</li>
     * <li>{@code IS_NULLABLE} — 是否可空（YES/NO 或 Y/N）</li>
     * </ul>
     *
     * <p>
     * 各方言通过 {@code AS} 别名统一列名，使调用方无需关心数据库差异。
     * SqlExecutor 返回的 JSON key 经大写化后与上述别名一致。
     * </p>
     *
     * @return 参数化 SQL 文本（含一个 {@code ?} 占位符）
     */
    String queryTableSchemaSql();
}
