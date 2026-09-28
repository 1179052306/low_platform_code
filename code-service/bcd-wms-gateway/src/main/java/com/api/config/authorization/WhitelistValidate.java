package com.api.config.authorization;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.db.DbHelp;
import com.server.db.Tenant;
import com.server.generate.GenerateSqlImpl;
import com.server.tenant.TenantData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Configuration
@Service
public class WhitelistValidate {

    @Resource
    private DbHelp dbHelp;
    @Autowired
    TenantData tenantData;
    @Resource
    private GenerateSqlImpl generateSql;

    @Autowired
    Tenant tenant;

    private final String dbName = "project";

    // --- 核心存储结构 ---
    // Key: URL (String)
    // Value: 是否启用 (Boolean) -> true=允许, false=禁止
    // 使用 ConcurrentHashMap 保证多线程下的线程安全，且查询速度极快 (O(1))
    private final ConcurrentHashMap<String, Boolean> whitelistCache = new ConcurrentHashMap<>();


    /**
     * 核心校验方法
     * 性能：O(1) 哈希查找
     */
    public boolean redisValidate(String url) {

        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        if (whitelistCache.size() == 0) {
            refreshCacheFromDB();
        }
        // 1. 直接从内存 Map 中获取状态
        Boolean isActive = whitelistCache.get(url);

        // 2. 如果缓存中没找到（可能是新加的 URL），可以选择立即刷新一次缓存兜底
//        if (isActive == null) {
//            log.debug("白名单缓存未命中，尝试刷新缓存: {}", url);
////            refreshCacheFromDB();
//            // 刷新后再次检查
//            isActive = whitelistCache.get(url);
//        }

        // 3. 返回结果：
        // null -> URL 不在白名单中 -> 拒绝
        // false -> URL 在白名单但被禁用 (ACTIVE=0) -> 拒绝
        // true -> URL 在白名单且启用 (ACTIVE=1) -> 通过
        return isActive != null && isActive;
    }

    /**
     * 从数据库加载数据并更新本地缓存
     */
    private void refreshCacheFromDB() {
        try {
            if (tenant.getSingle()) {
                // 1. 构建 SQL
                GenerateSqlTran generateSqlTran = new GenerateSqlTran();
                generateSqlTran.set_strsql("SELECT URL, ACTIVE FROM S_WHITELIST T WHERE 1=1");

                // 2. 查询数据库
                List<Map<String, Object>> dbList = dbHelp.queryMap(dbName, generateSqlTran);

                // 3. 构建新的临时 Map (避免在构建过程中影响正在进行的查询)
                ConcurrentHashMap<String, Boolean> newCache = new ConcurrentHashMap<>();
                int enableCount = 0;

                for (Map<String, Object> row : dbList) {
                    String url = (String) row.get("URL");
                    String activeVal = (String) row.get("ACTIVE");

                    // 只有 "1" 算启用，其他都算禁用
                    boolean isEnabled = "1".equals(activeVal);
                    if (isEnabled) {
                        enableCount++;
                        newCache.put(url, isEnabled);
                    }
                }

                // 4. 原子替换旧缓存
                whitelistCache.clear();
                whitelistCache.putAll(newCache);

                log.info("白名单缓存刷新成功 | 总数: {} | 启用: {} | 禁用: {}",
                        newCache.size(), enableCount, (newCache.size() - enableCount));
            } else {
                JSONArray data = tenantData.getWhiteData();
                if (data.size() > 0) {
                    ConcurrentHashMap<String, Boolean> newCache = new ConcurrentHashMap<>();
                    int enableCount = 0;

                    for (int i = 0; i < data.size(); i++) {

                        JSONObject row = data.getJSONObject(i);
                        String url = row.getString("URL");
                        String activeVal = row.getString("ACTIVE");
                        // 只有 "1" 算启用，其他都算禁用
                        boolean isEnabled = "1".equals(activeVal);
                        if (isEnabled) enableCount++;
                        newCache.put(url, isEnabled);
                    }
                    // 4. 原子替换旧缓存
                    whitelistCache.clear();
                    whitelistCache.putAll(newCache);

                    log.info("白名单缓存刷新成功 | 总数: {} | 启用: {} | 禁用: {}",
                            newCache.size(), enableCount, (newCache.size() - enableCount));
                }


            }

        } catch (Exception e) {
            log.error("刷新白名单缓存失败，当前缓存保持不变", e);
        }
    }

    @Scheduled(cron = "0 0/3 * * * ?")
    public void refresh() throws Exception {
        refreshCacheFromDB();
    }

}