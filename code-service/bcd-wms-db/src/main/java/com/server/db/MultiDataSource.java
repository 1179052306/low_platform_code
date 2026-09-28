package com.server.db;

import com.zaxxer.hikari.HikariDataSource;
import io.seata.rm.datasource.DataSourceProxy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @Description 多数据源路由 —— 旧池回收版。
 * <p>原版缺陷：reset 时把所有旧 resolved 池放入静态 Map 仅供异步关闭，
 * 一旦有持续借出未归还的连接，旧池永远关不掉 —— 每 3 分钟的定时刷新导致连接池
 * 累积泄漏，最终耗尽连接/线程资源。</p>
 *
 * <p>本版修复要点：</p>
 * <ol>
 *   <li>旧池移入实例级 {@link #pendingClose}（非 static，避免跨实例/跨应用共享）</li>
 *   <li>{@link CloseService} 周期性尝试关闭：空闲池立刻关闭，活跃池超时（默认 60s）后强制关闭并 WARN</li>
 *   <li>每次 reset 记录旧池数量、关闭动作，确保泄漏闭合可观测</li>
 * </ol>
 *
 * @author liwei
 * @date 2026/9/9
 */
@Slf4j
public class MultiDataSource extends AbstractRoutingDataSource {

    /** 待关闭旧池队列（实例级；COW 保证迭代与 remove 并发安全） */
    private final List<PoolHolder> pendingClose = new CopyOnWriteArrayList<PoolHolder>();

    /** 旧池强制关闭宽限期（毫秒）。超过该时间仍有活跃连接 → 强制关闭并 WARN。 */
    public static final long GRACE_PERIOD_MS = 60_000L;

    @Autowired
    private DBProperties dbProperties;

    @Override
    protected Object determineCurrentLookupKey() {
        return MultiDataSourceHolder.getDatasource();
    }

    /**
     * 重置数据源：旧池移入待关闭队列（不立即强关，避免打断进行中的事务），新池接管路由。
     *
     * @param targetDataSources 新数据源集合（dbName → HikariDataSource）
     */
    public void resetTargetDataSource(HashMap<Object, Object> targetDataSources) {
        try {
            if (targetDataSources == null || targetDataSources.isEmpty()) {
                return;
            }
            // 1. 把当前 resolved 旧池入队待关闭（带时间戳用于超时判定）
            //    注意：首次调用前 AbstractRoutingDataSource 未 afterPropertiesSet()，
            //    getResolvedDataSources() 会抛 IllegalStateException——按"没有旧池"处理即可
            Map<Object, DataSource> resolved = null;
            try {
                resolved = this.getResolvedDataSources();
            } catch (IllegalStateException firstCall) {
                log.debug("MultiDataSource reset (first call): no resolved data sources yet");
            }
            if (resolved != null) {
                for (Map.Entry<Object, DataSource> entry : resolved.entrySet()) {
                    DataSource ds = entry.getValue();
                    HikariDataSource hikari = unwrap(ds);
                    if (hikari != null) {
                        pendingClose.add(new PoolHolder(hikari, System.currentTimeMillis(), String.valueOf(entry.getKey())));
                    }
                }
                log.info("MultiDataSource reset: old pools queued for close, pendingCloseSize={}", pendingClose.size());
            }

            // 2. 按需包一层 Seata 代理
            boolean seataEnabled = isSeataEnabled();
            for (Object key : targetDataSources.keySet()) {
                if (seataEnabled) {
                    this.setDefaultTargetDataSource(new DataSourceProxy((HikariDataSource) targetDataSources.get(key)));
                } else {
                    this.setDefaultTargetDataSource(targetDataSources.get(key));
                }
            }

            // 3. 接管新池
            this.setTargetDataSources(targetDataSources);
            this.afterPropertiesSet();
        } catch (Exception e) {
            log.error("resetTargetDataSource failed: {}", e.toString(), e);
        }
    }

    /** 给 CloseService 调用：返回待关闭池队列（只读） */
    public List<PoolHolder> getPendingClose() {
        return pendingClose;
    }

    /** 拆开可能的 Seata DataSourceProxy 包装 */
    private static HikariDataSource unwrap(DataSource ds) {
        if (ds instanceof HikariDataSource) {
            return (HikariDataSource) ds;
        }
        if (ds instanceof DataSourceProxy) {
            return (HikariDataSource) ((DataSourceProxy) ds).getTargetDataSource();
        }
        return null;
    }

    private static boolean isSeataEnabled() {
        try {
            YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
            factoryBean.setResources(new ClassPathResource("application.yml"));
            Properties properties = factoryBean.getObject();
            if (properties == null) {
                return false;
            }
            return Boolean.parseBoolean(String.valueOf(properties.get("seata.enabled")));
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 待关闭池包装：含首次入队时间戳，供 CloseService 判定是否超时强关。
     */
    public static final class PoolHolder {
        private final HikariDataSource pool;
        private final long firstSeenMs;
        private final String dbName;

        public PoolHolder(HikariDataSource pool, long firstSeenMs, String dbName) {
            this.pool = pool;
            this.firstSeenMs = firstSeenMs;
            this.dbName = dbName;
        }

        public HikariDataSource pool() { return pool; }
        public long firstSeenMs() { return firstSeenMs; }
        public String dbName() { return dbName; }
    }
}