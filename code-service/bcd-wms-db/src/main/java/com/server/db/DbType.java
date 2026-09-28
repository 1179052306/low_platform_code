package com.server.db;

/**
 * @Description 数据库类型标识常量（与数据源配置中 type 字段、DbConfig.getType 返回值一致）
 * @Author liwei
 * @Date 2026/9/8
 *
 * <p>全库统一使用本常量替代 "0"~"4" 魔法值；新增数据库类型时在此追加常量，
 * 并在 com.server.syntax.val 包下新增对应 DbValueServer 方言实现（通过 dbType() 关联）。</p>
 **/
public final class DbType {

    /** MySQL */
    public static final String MYSQL = "0";
    /** Oracle */
    public static final String ORACLE = "1";
    /** SQLServer */
    public static final String SQLSERVER = "2";
    /** PostgreSQL */
    public static final String POSTGRESQL = "3";
    /** 达梦 DM */
    public static final String DM = "4";

    private DbType() {
    }
}
