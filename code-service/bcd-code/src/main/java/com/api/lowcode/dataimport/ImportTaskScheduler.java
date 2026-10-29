package com.api.lowcode.dataimport;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 导入任务调度器：@Scheduled 轮询捞 PENDING → CAS 抢占 → 提交导入线程池。
 * 任务表是唯一队列（内存不积压），CAS 原子抢占天然兼容未来多实例。
 * 附带：启动崩溃恢复（RUNNING→FAILED）与终态文件清理。
 */
@Slf4j
@Component
public class ImportTaskScheduler {

    @Resource
    private ImportTaskService taskService;

    @Resource
    private ImportPipelineExecutor pipelineExecutor;

    @Resource
    private ImportFileStore fileStore;

    @Resource
    private ImportProperties properties;

    @Resource
    private org.springframework.transaction.PlatformTransactionManager txManager;

    private DataSource txDataSource;

    private ThreadPoolExecutor executor;

    private final AtomicInteger threadSeq = new AtomicInteger(1);

    @PostConstruct
    public void init() {
        this.txDataSource = ((DataSourceTransactionManager) txManager).getDataSource();
        this.executor = new ThreadPoolExecutor(
                properties.getMaxConcurrent(), properties.getMaxConcurrent(),
                60L, TimeUnit.SECONDS, new SynchronousQueue<>(),
                r -> {
                    Thread t = new Thread(r, "import-executor-" + threadSeq.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
        taskService.ensureTable();
        recoverInterruptedTasks();
        log.info("[ImportScheduler] 初始化完成 maxConcurrent={}", properties.getMaxConcurrent());
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    /** 启动崩溃恢复：RUNNING → FAILED（服务重启中断），PENDING 保留由调度继续捞 */
    private void recoverInterruptedTasks() {
        try {
            int n = new JdbcTemplate(txDataSource).update(
                    "UPDATE " + ImportTaskService.TABLE + " SET status = ?, error_msg = ?, finished_at = ? WHERE status = ?",
                    ImportTaskService.STATUS_FAILED, "服务重启中断，请重新导入",
                    new Timestamp(System.currentTimeMillis()), ImportTaskService.STATUS_RUNNING);
            if (n > 0) {
                log.warn("[ImportScheduler] 恢复了 {} 个中断任务（标记 FAILED）", n);
            }
        } catch (Exception e) {
            log.error("[ImportScheduler] 崩溃恢复失败: {}", e.getMessage());
        }
    }

    /** 每 2 秒：RUNNING 不足 maxConcurrent 时按创建时间捞 PENDING，CAS 抢占后提交线程池 */
    @Scheduled(fixedDelay = 2000)
    public void schedule() {
        try {
            JdbcTemplate jdbc = new JdbcTemplate(txDataSource);
            Integer running = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + ImportTaskService.TABLE + " WHERE status = ?",
                    Integer.class, ImportTaskService.STATUS_RUNNING);
            int free = properties.getMaxConcurrent() - (running != null ? running : 0);
            if (free <= 0) return;

            List<String> pendings = jdbc.queryForList(
                    "SELECT task_id FROM " + ImportTaskService.TABLE + " WHERE status = ? ORDER BY created_at ASC",
                    String.class, ImportTaskService.STATUS_PENDING);
            for (String taskId : pendings) {
                if (free <= 0) break;
                int taken = jdbc.update(
                        "UPDATE " + ImportTaskService.TABLE + " SET status = ?, started_at = ? "
                                + "WHERE task_id = ? AND status = ?",
                        ImportTaskService.STATUS_RUNNING, new Timestamp(System.currentTimeMillis()),
                        taskId, ImportTaskService.STATUS_PENDING);
                if (taken > 0) {
                    free--;
                    submit(taskId);
                }
            }
        } catch (Exception e) {
            log.error("[ImportScheduler] 调度失败: {}", e.getMessage());
        }
    }

    private void submit(String taskId) {
        try {
            executor.execute(() -> {
                try {
                    pipelineExecutor.execute(taskId);
                } catch (Throwable e) {
                    log.error("[ImportScheduler] 任务执行异常: taskId={}, {}", taskId, e.getMessage(), e);
                    try {
                        taskService.finish(taskId, ImportTaskService.STATUS_FAILED,
                                "任务执行异常: " + e.getMessage(), null);
                    } catch (Exception ignore) {
                        // 终态兜底失败仅记录
                    }
                }
            });
            log.info("[ImportScheduler] 任务已提交执行: {}", taskId);
        } catch (Exception rejected) {
            // 线程池满（并发兜底）：回退 PENDING 等下轮
            new JdbcTemplate(txDataSource).update(
                    "UPDATE " + ImportTaskService.TABLE + " SET status = ? WHERE task_id = ? AND status = ?",
                    ImportTaskService.STATUS_PENDING, taskId, ImportTaskService.STATUS_RUNNING);
            log.warn("[ImportScheduler] 线程池拒绝，任务回退 PENDING: {}", taskId);
        }
    }

    /** 每小时：清理超期终态文件 */
    @Scheduled(fixedDelay = 3600000)
    public void cleanFiles() {
        try {
            int deleted = fileStore.cleanExpired();
            if (deleted > 0) {
                log.info("[ImportScheduler] 清理了 {} 个超期导入文件", deleted);
            }
        } catch (Exception e) {
            log.warn("[ImportScheduler] 文件清理失败: {}", e.getMessage());
        }
    }

    /** 供进度查询判断任务是否活跃 */
    public JSONObject runningInfo(String taskId) {
        return taskService.findById(taskId);
    }
}