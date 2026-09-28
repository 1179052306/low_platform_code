package com.api.config.authorization;

import com.alibaba.fastjson2.JSONArray;
import com.api.model.RequestRes;
import com.github.benmanes.caffeine.cache.Cache;
import com.server.db.DbHelp;
import com.server.db.MultiDataSourceHolder;
import com.server.db.Tenant;
import com.server.generate.GenerateSqlImpl;
import com.server.redis.RedisService;
import com.server.tenant.TenantData;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@Service
public class RequestValidate {

    public final static String LoginToken = "login_tokens:";

    // --- 1. 移除了全量的 Static Map (防止内存溢出) ---
    // 原来的 tenantListJurisdictions, tenantListPages, tenantListPageApis 已删除

    // --- 2. 注入 Caffeine 本地缓存 ---
    @Resource
    private Cache<String, Boolean> permissionLocalCache;

    // --- 3. 注入 Redisson 分布式锁 ---
    @Resource
    private RedissonClient redissonClient;

    // --- 原有属性保持不变 ---
    private final String dbName = "project";
    // 注意：这里只查询 COUNT，不查询具体内容，为了适配 50万数据量
    private final String PageApiCountSql = "SELECT COUNT(1) as CNT FROM (SELECT T.JURISDICTION_KEY,T.PAGE_KEY,P.API_URL,S.SYSTEM_TYPE FROM S_JURISDICTION_PAGE_API T LEFT JOIN S_PAGE_API P ON T.PAGE_API_KEY=P.PAGE_API_KEY LEFT JOIN S_PAGE S ON T.PAGE_KEY=S.PAGE_KEY) T WHERE 1=1 ";

    @Resource
    private DbHelp dbHelp;
    @Resource
    private RedisService redisService;
    @Resource
    private GenerateSqlImpl generateSql;
    @Resource
    private WhitelistValidate whitelistValidate;
    @Resource
    private Tenant tenant;
    @Resource
    private TenantData tenantData;

    // ==================================================================
    // === 核心鉴权逻辑：修改为 基于 Key 的缓存查询模式 ===
    // ==================================================================

    public boolean redisValidate(RequestRes requestRes) throws Exception {
        // 1. 构建权限校验的唯一 Key (包含租户隔离)
        String permKey = buildPermissionKey(requestRes);

        // 2. 1级缓存：Caffeine (本地内存)
        Boolean localHit = permissionLocalCache.getIfPresent(permKey);
        if (localHit != null) {
            return localHit;
        }else{
            if (whitelistValidate.redisValidate(requestRes.getPageUrl())) {
                return true;
            }
        }

        // 3. 2级缓存：Redis
        // 使用 Redis 存储 "permKey" -> "1" (存在) 或 "0" (不存在)
        String redisHit = redisService.getCacheObject(permKey);
        if ("1".equals(redisHit)) {
            permissionLocalCache.put(permKey, true);
            return true;
        } else if ("0".equals(redisHit)) {
            return false;
        }

        // 4. 3级缓存：数据库查询 (带分布式锁防击穿)
        return loadFromDatabaseWithLock(permKey, requestRes);
    }

    /**
     * 带分布式锁的数据库加载
     */
    private boolean loadFromDatabaseWithLock(String permKey, RequestRes requestRes) {
        String lockKey = "lock:perm:" + permKey;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁，等待100ms，持有10s
            boolean isLocked = lock.tryLock(100, 10, TimeUnit.SECONDS);
            if (isLocked) {
                try {
                    // 双重检查
                    Boolean localRetry = permissionLocalCache.getIfPresent(permKey);
                    if (localRetry != null) return localRetry;

                    String redisRetry = redisService.getCacheObject(permKey);
                    if ("1".equals(redisRetry)) return true;
                    if ("0".equals(redisRetry)) return false;

                    // 执行数据库查询
                    boolean hasPerm = queryDatabase(requestRes);

                    // 回填缓存
                    String redisValue = hasPerm ? "1" : "0";
                    // 有权限缓存1小时，无权限缓存6秒(防止频繁查库)
                    long expireTime = hasPerm ? 3600 : 6;

                    redisService.setCacheObject(permKey, redisValue, expireTime, TimeUnit.SECONDS);
                    if(hasPerm){
                        permissionLocalCache.put(permKey, hasPerm);
                    }
                    return hasPerm;
                } finally {
                    lock.unlock();
                }
            } else {
                // 获取锁失败，降级为直接查询数据库（防止线程饥饿）
                log.warn("Failed to acquire lock for key: {}", permKey);
                return false;
            }
        } catch (Exception e) {
            log.error("Lock error", e);
            return false; // 异常降级
        }
    }

    /**
     * 数据库查询逻辑 (复用你原有的工具类)
     * 逻辑：查询 COUNT 而不是全表数据
     */
    private boolean queryDatabase(RequestRes requestRes) {
        try {
            if (!tenant.getSingle()) {
                MultiDataSourceHolder.setDatasource(requestRes.getTenant());
            }

            // 构建查询条件
            GenerateSqlTran sqlTran = new GenerateSqlTran();
            JSONArray where = new JSONArray();

            // 复用你原有的 generateSql.add 方法构建条件
            generateSql.add(where, "JURISDICTION_KEY", requestRes.getJurisdiction());
            generateSql.add(where, "PAGE_KEY", requestRes.getPageKey());
            generateSql.add(where, "API_URL", requestRes.getPageUrl());

            // 构建 SQL (SELECT COUNT...)
            StringBuilder sqlBuilder = new StringBuilder(PageApiCountSql);
            sqlTran = generateSql.sqlQuery(dbName, "S_JURISDICTION_PAGE_API", sqlBuilder, where);

            // 执行查询
            JSONArray result = dbHelp.queryJson(dbName, sqlTran);
            if (result.size() > 0) {
                int count = result.getJSONObject(0).getIntValue("CNT");
                return count > 0;
            }
            return false;

        } catch (Exception e) {
            log.error("DB Query Error", e);
            return false; // 数据库异常默认拒绝，防止权限绕过
        } finally {
            if (!tenant.getSingle()) {
                MultiDataSourceHolder.clearDataSource();
            }
        }
    }

    /**
     * 构建权限 Key
     * 格式: TenantId:Jurisdiction:PageKey:HashedUrl
     */
    private String buildPermissionKey(RequestRes req) {
        // URL 可能很长，做 Hash 处理以节省内存
        String urlHash = String.valueOf(req.getPageUrl().hashCode());
        if (tenant.getSingle()) {
            return "SINGLE:" + req.getJurisdiction() + ":" + req.getPageKey() + ":" + urlHash;
        } else {
            return req.getTenant() + ":" + req.getJurisdiction() + ":" + req.getPageKey() + ":" + urlHash;
        }
    }


    public boolean getLoginToekn(String Btoken, String tenantId) {
        String token = Btoken.substring(Btoken.lastIndexOf(" ") + 1);
        String cacheKey;
        if (tenant.getSingle()) {
            cacheKey = LoginToken + token;
        } else {
            cacheKey = tenantId + ":" + LoginToken + token;
        }

        Boolean localResult = permissionLocalCache.getIfPresent(cacheKey);
        if (localResult != null) {
            return localResult;
        }

        String loginTokenFromRedis = redisService.getCacheObject(cacheKey);
        if (!StringUtil.isNullOrEmpty(loginTokenFromRedis)) {
            permissionLocalCache.put(cacheKey, true);
            return true;
        }

        try {
            if (!tenant.getSingle()) {
                MultiDataSourceHolder.setDatasource(tenantId);
            }
            GenerateSqlTran tokenInSqlTran = new GenerateSqlTran();
            JSONArray Where = new JSONArray();
            generateSql.add(Where, "LOGIN_TOKEN", token);
            String Sql = "SELECT LOGIN_TOKEN FROM LOGIN_TOKEN T WHERE 1=1";
            tokenInSqlTran = this.generateSql.sqlQuery(dbName, "LOGIN_TOKEN", new StringBuilder(Sql), Where);
            int lt = dbHelp.queryCount(dbName, tokenInSqlTran);
            if (lt == 0) {
                return false;
            }
            permissionLocalCache.put(cacheKey, true);
            redisService.setCacheObject(cacheKey, cacheKey, 1L, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("数据处理出现异常! 异常原因:" + e);
            return false;
        } finally {
            if (!tenant.getSingle()) {
                MultiDataSourceHolder.clearDataSource();
            }
        }
        return true;
    }


}