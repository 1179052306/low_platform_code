package com.api.lowcode.dataimport;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.common.BizException;
import com.server.sqlengine.dialect.SqlDialect;
import com.server.sqlengine.dialect.SqlDialectRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.List;


/**
 * 导入任务生命周期：懒建表、三层准入（用户/表/全局）、创建、进度查询、取消、历史。
 * 任务表 lc_import_task 是唯一事实源（持久化队列 + 进度载体）。
 */
@Slf4j
@Service
public class ImportTaskService {

    public static final String TABLE = "lc_import_task";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private DataSource txDataSource;
    private TransactionTemplate txTemplate;

    @Resource
    private PlatformTransactionManager txManager;

    @Resource
    private ImportProperties properties;

    @Resource
    private SqlDialectRegistry dialectRegistry;

    private volatile boolean tableChecked = false;

    @PostConstruct
    public void init() {
        this.txDataSource = ((DataSourceTransactionManager) txManager).getDataSource();
        TransactionTemplate t = new TransactionTemplate(txManager);
        t.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        t.setTimeout(5);
        this.txTemplate = t;
    }

    /** 懒建任务表（dialect 兼容 7 库，CREATE 成功一次后跳过）—— 调度器/Controller 共用 */
    public synchronized void ensureTable() {
        if (tableChecked) return;
        try {
            txTemplate.execute(status -> {
                JdbcTemplate jdbc = new JdbcTemplate(txDataSource);
                SqlDialect dialect = dialectRegistry.dialect("lowcode");
                if (jdbc.queryForList(dialect.checkTableExistsSql(), Integer.class, TABLE).isEmpty()) {
                    jdbc.execute("CREATE TABLE " + TABLE + " ("
                            + "task_id VARCHAR(64) PRIMARY KEY, "
                            + "biz_code VARCHAR(128) NOT NULL, "
                            + "file_name VARCHAR(512), "
                            + "file_path VARCHAR(1024), "
                            + "status VARCHAR(20) NOT NULL, "
                            + "total_rows BIGINT DEFAULT 0, "
                            + "processed_rows BIGINT DEFAULT 0, "
                            + "success_rows BIGINT DEFAULT 0, "
                            + "failed_rows BIGINT DEFAULT 0, "
                            + "error_file_path VARCHAR(1024), "
                            + "error_msg VARCHAR(2048), "
                            + "created_by VARCHAR(64), "
                            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                            + "started_at TIMESTAMP, "
                            + "finished_at TIMESTAMP, "
                            + "cancel_flag SMALLINT DEFAULT 0)");
                    jdbc.execute("CREATE INDEX idx_import_task_status ON " + TABLE + " (status, created_at)");
                    jdbc.execute("CREATE INDEX idx_import_task_user ON " + TABLE + " (created_by, status)");
                    log.info("[ImportTask] 已创建任务表 {}", TABLE);
                }
                return null;
            });
            tableChecked = true;
        } catch (Exception e) {
            log.error("[ImportTask] 任务表检查/创建失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 三层准入 + 创建 PENDING 任务（taskId 由调用方生成，与暂存文件名保持一致）。
     * 用户级：同人同时 1 个非终态任务；表级：同表同时 1 个非终态任务（PENDING 也占用）。
     */
    public JSONObject createTask(String taskId, String bizCode, String fileName, String filePath, String createdBy) {
        ensureTable();
        JdbcTemplate jdbc = new JdbcTemplate(txDataSource);
        Integer userActive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE created_by = ? AND status IN (?, ?)",
                Integer.class, createdBy, STATUS_PENDING, STATUS_RUNNING);
        if (userActive != null && userActive > 0) {
            throw new BizException("IMPORT_TASK_EXISTS", "您有导入任务正在进行中或排队中，请等待完成后再导入");
        }
        Integer tableActive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE biz_code = ? AND status IN (?, ?)",
                Integer.class, bizCode, STATUS_PENDING, STATUS_RUNNING);
        if (tableActive != null && tableActive > 0) {
            throw new BizException("IMPORT_TASK_EXISTS", "该表已有导入任务排队或执行中，请稍后再试");
        }
        jdbc.update("INSERT INTO " + TABLE + " (task_id, biz_code, file_name, file_path, status, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                taskId, bizCode, fileName, filePath, STATUS_PENDING, createdBy, new Timestamp(System.currentTimeMillis()));
        JSONObject task = new JSONObject();
        task.put("taskId", taskId);
        task.put("sync", false);
        task.put("queuePosition", queuePosition(taskId));
        return task;
    }

    /** 排队位置（前面还有几个 PENDING） */
    public int queuePosition(String taskId) {
        JdbcTemplate jdbc = new JdbcTemplate(txDataSource);
        JSONObject row = findById(taskId);
        if (row == null || !STATUS_PENDING.equals(row.getString("STATUS"))) return 0;
        Integer pos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE status = ? AND created_at < ?",
                Integer.class, STATUS_PENDING, row.getDate("CREATED_AT"));
        return pos != null ? pos : 0;
    }

    public JSONObject findById(String taskId) {
        List<JSONObject> rows = new JdbcTemplate(txDataSource).query(
                "SELECT * FROM " + TABLE + " WHERE task_id = ?",
                (rs, i) -> {
                    JSONObject o = new JSONObject();
                    o.put("TASK_ID", rs.getString("task_id"));
                    o.put("BIZ_CODE", rs.getString("biz_code"));
                    o.put("FILE_NAME", rs.getString("file_name"));
                    o.put("STATUS", rs.getString("status"));
                    o.put("TOTAL_ROWS", rs.getLong("total_rows"));
                    o.put("PROCESSED_ROWS", rs.getLong("processed_rows"));
                    o.put("SUCCESS_ROWS", rs.getLong("success_rows"));
                    o.put("FAILED_ROWS", rs.getLong("failed_rows"));
                    o.put("ERROR_FILE_PATH", rs.getString("error_file_path"));
                    o.put("ERROR_MSG", rs.getString("error_msg"));
                    o.put("CANCEL_FLAG", rs.getInt("cancel_flag"));
                    o.put("CREATED_BY", rs.getString("created_by"));
                    o.put("CREATED_AT", rs.getTimestamp("created_at"));
                    o.put("STARTED_AT", rs.getTimestamp("started_at"));
                    o.put("FINISHED_AT", rs.getTimestamp("finished_at"));
                    return o;
                }, taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 对外进度视图（camelCase，含排队位置与错误文件标记） */
    public JSONObject taskView(String taskId) {
        JSONObject row = findById(taskId);
        if (row == null) {
            throw new BizException("IMPORT_TASK_NOT_FOUND", "导入任务不存在: " + taskId);
        }
        JSONObject view = new JSONObject();
        view.put("taskId", row.getString("TASK_ID"));
        view.put("bizCode", row.getString("BIZ_CODE"));
        view.put("status", row.getString("STATUS"));
        view.put("totalRows", row.getLong("TOTAL_ROWS"));
        view.put("processedRows", row.getLong("PROCESSED_ROWS"));
        view.put("successRows", row.getLong("SUCCESS_ROWS"));
        view.put("failedRows", row.getLong("FAILED_ROWS"));
        view.put("queuePosition", STATUS_PENDING.equals(row.getString("STATUS")) ? queuePosition(taskId) : 0);
        view.put("errorMsg", row.getString("ERROR_MSG"));
        String errorFile = row.getString("ERROR_FILE_PATH");
        view.put("hasErrorFile", errorFile != null && !errorFile.isEmpty());
        view.put("createdAt", row.get("CREATED_AT"));
        view.put("startedAt", row.get("STARTED_AT"));
        view.put("finishedAt", row.get("FINISHED_AT"));
        return view;
    }

    /** 取消：置 cancel_flag，流水线批间检查生效（PENDING 直接终态） */
    public boolean cancel(String taskId) {
        ensureTable();
        JdbcTemplate jdbc = new JdbcTemplate(txDataSource);
        JSONObject row = findById(taskId);
        if (row == null) return false;
        String status = row.getString("STATUS");
        if (STATUS_PENDING.equals(status)) {
            return jdbc.update("UPDATE " + TABLE + " SET status = ?, finished_at = ? WHERE task_id = ? AND status = ?",
                    STATUS_CANCELLED, new Timestamp(System.currentTimeMillis()), taskId, STATUS_PENDING) > 0;
        }
        if (STATUS_RUNNING.equals(status)) {
            jdbc.update("UPDATE " + TABLE + " SET cancel_flag = 1 WHERE task_id = ?", taskId);
            return true;
        }
        return false;
    }

    /** 历史列表（按创建人过滤，倒序；跨库免 OFFSET/LIMIT 方言差异，内存分页——单人任务量小） */
    public JSONObject listTasks(String bizCode, String createdBy, int pageNum, int pageSize) {
        ensureTable();
        JdbcTemplate jdbc = new JdbcTemplate(txDataSource);
        StringBuilder where = new StringBuilder(" WHERE created_by = ?");
        Object[] args;
        if (bizCode != null && !bizCode.isEmpty()) {
            where.append(" AND biz_code = ?");
            args = new Object[]{createdBy, bizCode};
        } else {
            args = new Object[]{createdBy};
        }
        List<JSONObject> all = jdbc.query(
                "SELECT task_id, biz_code, file_name, status, total_rows, success_rows, failed_rows, "
                        + "error_msg, created_at, finished_at, error_file_path FROM " + TABLE + where
                        + " ORDER BY created_at DESC",
                (rs, i) -> {
                    JSONObject o = new JSONObject();
                    o.put("taskId", rs.getString("task_id"));
                    o.put("bizCode", rs.getString("biz_code"));
                    o.put("fileName", rs.getString("file_name"));
                    o.put("status", rs.getString("status"));
                    o.put("totalRows", rs.getLong("total_rows"));
                    o.put("successRows", rs.getLong("success_rows"));
                    o.put("failedRows", rs.getLong("failed_rows"));
                    o.put("errorMsg", rs.getString("error_msg"));
                    o.put("createdAt", rs.getTimestamp("created_at"));
                    o.put("finishedAt", rs.getTimestamp("finished_at"));
                    String ef = rs.getString("error_file_path");
                    o.put("hasErrorFile", ef != null && !ef.isEmpty());
                    return o;
                }, args);
        int total = all.size();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(total, from + pageSize);
        List<JSONObject> page = from < total ? all.subList(from, to) : new java.util.ArrayList<>();
        JSONObject result = new JSONObject();
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        result.put("list", page);
        return result;
    }

    /** 更新进度（流水线每批调用，节流由调用方控制） */
    public void updateProgress(String taskId, long totalRows, long processedRows, long successRows, long failedRows) {
        new JdbcTemplate(txDataSource).update(
                "UPDATE " + TABLE + " SET total_rows = ?, processed_rows = ?, success_rows = ?, failed_rows = ? WHERE task_id = ?",
                totalRows, processedRows, successRows, failedRows, taskId);
    }

    /** 终态落库 */
    public void finish(String taskId, String status, String errorMsg, String errorFilePath) {
        new JdbcTemplate(txDataSource).update(
                "UPDATE " + TABLE + " SET status = ?, error_msg = ?, error_file_path = ?, finished_at = ? WHERE task_id = ?",
                status, errorMsg, errorFilePath, new Timestamp(System.currentTimeMillis()), taskId);
    }

    public boolean isCancelRequested(String taskId) {
        JSONObject row = findById(taskId);
        return row != null && row.getIntValue("CANCEL_FLAG") == 1;
    }
}