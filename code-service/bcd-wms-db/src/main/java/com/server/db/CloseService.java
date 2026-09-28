//package com.server.db;
//
//import com.zaxxer.hikari.HikariDataSource;
//import com.zaxxer.hikari.HikariPoolMXBean;
//import lombok.extern.slf4j.Slf4j;
//
//
///**
// * 旧池回收周期任务 —— 修复连接池泄漏的关键。
// *
// * <p>三种关闭路径：</p>
// * <ol>
// *   <li>MXBean==null：池已关闭（Hikari 关闭后 MXBean 自动失效），从队列移除（幂等）</li>
// *   <li>活跃连接=0：立即关闭并从队列移除</li>
// *   <li>活跃连接>0 但已超过宽限期（{@<Config> MultiDataSource.GRACE_PERIOD_MS}）：
// *       softEvictConnections 让活跃连接在下一次归还时被驱逐，然后 close 池；记录 WARN 供排查未归还连接的业务 bug</li>
// * </ol>
// *
// * <p>原版缺陷：仅当活跃连接=0 才关闭；活跃连接>0 永久累积。修复后即使有长连接借用，
// * 宽限期一到也会强制关闭，避免每 3 分钟定时刷新导致的池累积泄漏。</p>
// *
// * @author lw
// * @date 2025/1/8
// */
//@Slf4j
//public class CloseService extends ThreadBase {
//
//
//    private final MultiDataSource multiDataSource;
//
//    public CloseService(ThreadsObject threadsObject, MultiDataSource multiDataSource) {
//        super(threadsObject);
//        this.threadsObject = threadsObject;
//        this.multiDataSource = multiDataSource;
//    }
//
//    @Override
//    public void execute() throws Exception {
//        if (multiDataSource == null) {
//            return;
//        }
//        long now = System.currentTimeMillis();
//        for (MultiDataSource.PoolHolder holder : multiDataSource.getPendingClose()) {
//            HikariDataSource pool = holder.pool();
//            if (pool == null) {
//                continue;
//            }
//            try {
//                HikariPoolMXBean mxBean = pool.getHikariPoolMXBean();
//                if (mxBean == null) {
//                    // 池已关闭（幂等移除）
//                    multiDataSource.getPendingClose().remove(holder);
//                    continue;
//                }
//                int total = mxBean.getTotalConnections();
//                int idle = mxBean.getIdleConnections();
//                int active = total - idle;
//                if (active == 0) {
//                    pool.close();
//                    multiDataSource.getPendingClose().remove(holder);
//                    log.info("CloseService: closed idle pool dbName={} (待关闭队列剩余 {})",
//                            holder.dbName(), multiDataSource.getPendingClose().size());
//                } else if (now - holder.firstSeenMs() > MultiDataSource.GRACE_PERIOD_MS) {
//                    // 超过宽限期仍有活跃连接 → 强关（不依赖 softEvictConnections，避免 Hikari 版本差异）；
//                    // Hikari close() 会等待 idleTimeout 后再彻底关，活跃连接归还时拿到的连接会被标记废弃
//                    pool.close();
//                    multiDataSource.getPendingClose().remove(holder);
//                    log.warn("CloseService: forced-close pool dbName={} after grace={}ms with active={} connection(s) (检查是否有未归还的 Connection)",
//                            holder.dbName(), MultiDataSource.GRACE_PERIOD_MS, active);
//                }
//            } catch (Exception ex) {
//                log.error("CloseService: 关闭旧池 dbName={} 异常：{}", holder.dbName(), ex.toString());
//            }
//        }
//    }
//}