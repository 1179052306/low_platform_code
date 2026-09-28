package com.server.db;

import com.zaxxer.hikari.HikariDataSource;
import io.seata.rm.datasource.DataSourceProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Locale;
import java.util.Map;


/**
 * @Description 数据库配置读取
 * <p>修复点：所有 {@code .getXxx()} 链路上的 null 入参与 null 字段都做容错；
 * 未知 dbName 不再返回 null（默认 {@link DbType#MYSQL}），避免下游 NPE；</p>
 * <p>类型标识全部使用 {@link DbType} 常量（替代散落魔法值 "0"~"4"）。</p>
 *
 * @Author liwei
 * @Date 2023/7/26
 */
@Service
public class DbConfig {

    private static final Logger log = LoggerFactory.getLogger(DbConfig.class);

    @Resource
    public MultiDataSource dataSource;

    @Resource
    Tenant tenant;

    @Resource
    InterceptTenant interceptTenant;

    /**
     * 获取数据库类型（基于 JDBC driver class）。
     *
     * @param dbName 数据源名，可为 null（null 时用 default）
     * @return 数据库类型；取不到时返回 {@link DbType#MYSQL} 而非 null，下游可放心使用
     */
    @Nullable
    public String getType(@Nullable String dbName) {
        String driverClassName = "";
        try {
            if (dataSource == null) {
                return DbType.MYSQL;
            }
            driverClassName = getConnType(dbName);
        } catch (Exception e) {
            log.warn("DbConfig.getType 获取 driverClassName 失败 dbName={}：{}", dbName, e.toString());
        }
        if (driverClassName == null) {
            return DbType.MYSQL;
        }
        String lower = driverClassName.toLowerCase(Locale.ROOT);
        if (lower.contains("mysql")) {
            return DbType.MYSQL;
        }
        if (lower.contains("oracle")) {
            return DbType.ORACLE;
        }
        if (lower.contains("sqlserver")) {
            return DbType.SQLSERVER;
        }
        if (lower.contains("postgresql")) {
            return DbType.POSTGRESQL;
        }
        if (lower.contains("dm")) {
            return DbType.DM;
        }
        // 未知驱动：默认 MySQL，避免下游方言查表返回 null
        return DbType.MYSQL;
    }

    /**
     * 获取数据库用户名
     */
    @Nullable
    public String getUserName(@Nullable String dbName) {
        if (dataSource == null) {
            return null;
        }
        try {
            return getUserNameVal(dbName);
        } catch (Exception e) {
            log.warn("DbConfig.getUserName 失败 dbName={}：{}", dbName, e.toString());
            return null;
        }
    }

    public String getConnType(@Nullable String dbName) throws Exception {
        HikariDataSource hikariDataSource = this.getHikariDataSource(dbName);
        if (hikariDataSource == null) {
            return null;
        }
        return hikariDataSource.getDriverClassName();
    }

    public String getUserNameVal(@Nullable String dbName) throws Exception {
        HikariDataSource hikariDataSource = this.getHikariDataSource(dbName);
        if (hikariDataSource == null) {
            return null;
        }
        return hikariDataSource.getUsername();
    }

    /**
     * 拿底层 HikariDataSource；解 Seata 代理包装。
     * <p>修复原 NPE：{@code resolvedDataSources.get(dbName)} 返回 null 时直接 .getClass() 抛 NPE，
     * 现统一返回 null（不抛异常）。同时兜底 Spring 的 "DataSources not resolved yet" 检查。</p>
     *
     * @return HikariDataSource，未配置/未初始化返回 null
     */
    @Nullable
    public HikariDataSource getHikariDataSource(@Nullable String dbName) {
        if (dataSource == null) {
            return null;
        }
        String lookupKey = resolveLookupKey(dbName);
        DataSource ds = null;
        try {
            if (lookupKey != null) {
                Map<Object, DataSource> resolved = dataSource.getResolvedDataSources();
                ds = resolved != null ? resolved.get(lookupKey) : null;
            } else {
                ds = dataSource.getResolvedDefaultDataSource();
            }
        } catch (IllegalStateException unresolved) {
            // AbstractRoutingDataSource 未 afterPropertiesSet 时抛 "DataSources not resolved yet"
            log.debug("DbConfig.getHikariDataSource: MultiDataSource 未初始化 dbName={}", dbName);
            return null;
        }
        return unwrap(ds);
    }

    /** 拆开可能的 Seata DataSourceProxy 包装；null/非包装安全返回 null */
    @Nullable
    private HikariDataSource unwrap(@Nullable DataSource ds) {
        if (ds == null) {
            return null;
        }
        if (ds instanceof HikariDataSource) {
            return (HikariDataSource) ds;
        }
        if (ds instanceof DataSourceProxy) {
            DataSource target = ((DataSourceProxy) ds).getTargetDataSource();
            return target instanceof HikariDataSource ? (HikariDataSource) target : null;
        }
        return null;
    }

    /** 还原数据源 lookup key：单租户直接 dbName；多租户拼接 tenantId；null 入参返回 null（走 default） */
    @Nullable
    private String resolveLookupKey(@Nullable String dbName) {
        if (dbName == null) {
            return null;
        }
        if (tenant == null || tenant.getSingle()) {
            return dbName;
        }
        if (interceptTenant == null) {
            return dbName;
        }
        String tenantId = interceptTenant.getTenantHeader();
        return dbName + "-" + (tenantId == null ? "" : tenantId);
    }

    /**
     * 由数据源用户名反查 dbName。
     * <p>修复：dbSourceName==null 时原代码会 NPE（toUpperCase on null）。</p>
     */
    @Nullable
    public String getDbName(@Nullable String dbSourceName) {
        if (dbSourceName == null || dataSource == null) {
            return null;
        }
        Map<Object, DataSource> resolved;
        try {
            resolved = dataSource.getResolvedDataSources();
        } catch (IllegalStateException unresolved) {
            log.debug("DbConfig.getDbName: MultiDataSource 未初始化 dbSourceName={}", dbSourceName);
            return null;
        }
        if (resolved == null) {
            return null;
        }
        String upperSrc = dbSourceName.toUpperCase(Locale.ROOT);
        try {
            if (tenant != null && tenant.getSingle()) {
                for (Map.Entry<Object, DataSource> entry : resolved.entrySet()) {
                    HikariDataSource hikari = unwrap(entry.getValue());
                    if (hikari == null || hikari.getUsername() == null) {
                        continue;
                    }
                    if (upperSrc.equals(hikari.getUsername().toUpperCase(Locale.ROOT))) {
                        return hikari.getPoolName();
                    }
                }
            } else if (interceptTenant != null) {
                String tenantId = interceptTenant.getTenantHeader();
                if (tenantId == null) {
                    return null;
                }
                for (Map.Entry<Object, DataSource> entry : resolved.entrySet()) {
                    HikariDataSource hikari = unwrap(entry.getValue());
                    if (hikari == null || hikari.getPoolName() == null || hikari.getUsername() == null) {
                        continue;
                    }
                    if (hikari.getPoolName().contains(tenantId)
                            && upperSrc.equals(hikari.getUsername().toUpperCase(Locale.ROOT))) {
                        return hikari.getPoolName();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("DbConfig.getDbName 失败 dbSourceName={}：{}", dbSourceName, e.toString());
        }
        return null;
    }

    public boolean checkTenant() {
        if (tenant == null) {
            return false;
        }
        if (tenant.getSingle()) {
            return true;
        }
        if (interceptTenant == null || dataSource == null) {
            return false;
        }
        Map<Object, DataSource> resolved;
        try {
            resolved = dataSource.getResolvedDataSources();
        } catch (IllegalStateException unresolved) {
            return false;
        }
        if (resolved == null) {
            return false;
        }
        String tenantId = interceptTenant.getTenantHeader();
        if (tenantId == null) {
            return false;
        }
        return resolved.get("project" + "-" + tenantId) != null;
    }

}