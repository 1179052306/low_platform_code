package com.server.sqlengine.dialect;

import com.server.db.DbConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL 方言注册表 —— Spring 自动收集全部 {@link SqlDialect} 实现，按 {@link SqlDialect#dbType()} 注册。
 * <p>设计参考现有 {@code com.server.syntax.val.DbValue} 的 fail-fast 注册机制，
 * 但仅服务于全新 SQL 引擎，不与旧方言体系耦合。</p>
 *
 * @author liwei
 * @date 2026/9/9
 */
@Service
public class SqlDialectRegistry {

    private final DbConfig dbConfig;
    private final Map<String, SqlDialect> registry = new HashMap<String, SqlDialect>();

    @Autowired
    public SqlDialectRegistry(DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @Autowired(required = false)
    public void registerDialects(List<SqlDialect> dialects) {
        if (dialects == null) {
            return;
        }
        for (SqlDialect dialect : dialects) {
            SqlDialect old = registry.put(dialect.dbType(), dialect);
            if (old != null) {
                throw new IllegalStateException("数据库类型 " + dialect.dbType()
                        + " 存在多个 SqlDialect 实现: " + dialect.getClass().getName()
                        + " 与 " + old.getClass().getName());
            }
        }
    }

    /**
     * 按数据源名解析方言。
     *
     * @throws IllegalStateException 类型未注册任何方言实现时抛出
     */
    public SqlDialect dialect(@Nullable String dbName) {
        String type = dbConfig.getType(dbName);
        SqlDialect d = registry.get(type);
        if (d == null) {
            throw new IllegalStateException("数据库类型[" + type + "]（dbName=" + dbName
                    + "）未注册 SqlDialect 实现，请在 " + SqlDialect.class.getPackage().getName()
                    + " 包下提供该类型的实现类");
        }
        return d;
    }
}
