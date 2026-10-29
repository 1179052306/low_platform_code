package com.api.lowcode.dataimport;

import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.common.BizException;
import com.api.lowcode.dataimport.parse.ImportParseService;
import com.api.lowcode.dataimport.parse.ParsedDocument;
import com.api.lowcode.dataimport.resolve.BaseDataReverseResolver;
import com.api.lowcode.dataimport.validate.ImportValidateService;
import com.api.lowcode.dataimport.write.MasterDetailWriter;
import com.api.lowcode.meta.BizMeta;
import com.api.lowcode.meta.MetaRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导入流水线：SAX 解析 → 校验（必填/类型/唯一）→ 基础资料反查 → 分批事务写入 → 错误报告。
 * 批间检查取消标记并节流更新进度；终态 SUCCESS / PARTIAL / FAILED / CANCELLED。
 */
@Slf4j
@Component
public class ImportPipelineExecutor {

    @Resource
    private MetaRegistry metaRegistry;

    @Resource
    private ImportTaskService taskService;

    @Resource
    private ImportTemplateService templateService;

    @Resource
    private ImportParseService parseService;

    @Resource
    private ImportValidateService validateService;

    @Resource
    private BaseDataReverseResolver reverseResolver;

    @Resource
    private MasterDetailWriter masterDetailWriter;

    @Resource
    private ImportFileStore fileStore;

    @Resource
    private ImportProperties properties;

    @Resource(name = "multiDataSource")
    private javax.sql.DataSource dataSource;


    public void execute(String taskId) {
        JSONObject task = taskService.findById(taskId);
        if (task == null) {
            log.error("[ImportPipeline] 任务不存在: {}", taskId);
            return;
        }
        String bizCode = task.getString("BIZ_CODE");
        String filePath = task.getString("FILE_PATH");
        if (filePath == null || filePath.trim().isEmpty()) {
            filePath = findFilePath(taskId);
        }

        log.info("[ImportPipeline] 开始执行: taskId={}, bizCode={}", taskId, bizCode);

        ImportErrorCollector collector = new ImportErrorCollector();
        try {
            BizMeta meta = metaRegistry.getMeta(bizCode);
            if (meta == null) {
                throw new BizException("META_NOT_FOUND", "业务元数据不存在: " + bizCode);
            }

            // 结构信息（与模板同源）
            JSONObject schema = templateService.loadPageSchema(bizCode);
            List<ImportTemplateService.DetailView> detailViews =
                    schema != null ? templateService.collectDetails(schema) : new ArrayList<>();
            List<String> masterFields = new ArrayList<>();
            for (com.api.lowcode.meta.FieldMeta f : meta.getFields()) {
                if (ImportTemplateService.isAuditField(f.getFieldName())) continue;
                masterFields.add(f.getFieldName());
            }
            List<Map.Entry<String, List<String>>> detailTables = new ArrayList<>();
            Map<String, String> keyExprByTable = new HashMap<>();
            for (ImportTemplateService.DetailView dv : detailViews) {
                List<String> fields = new ArrayList<>(dv.columnFields);
                fields.remove(dv.keyExpr);
                detailTables.add(new LinkedHashMap.SimpleEntry<>(dv.tableName, fields));
                keyExprByTable.put(dv.tableName, dv.keyExpr);
            }
            // 列头显示名映射（label → 字段名，解析 label 表头时反查；主表 label + 明细 caption）
            Map<String, String> headerLabels = new LinkedHashMap<>();
            if (schema != null) {
                for (Map.Entry<String, String> e : templateService.collectFieldLabels(schema).entrySet()) {
                    headerLabels.put(e.getValue(), e.getKey());
                }
            }
            for (ImportTemplateService.DetailView dv : detailViews) {
                for (Map.Entry<String, String> e : dv.captionByField.entrySet()) {
                    headerLabels.putIfAbsent(e.getValue(), e.getKey());
                }
            }

            // ① 解析
            ParsedDocument doc;
            try (InputStream in = Files.newInputStream(Paths.get(filePath))) {
                ImportParseService.ParseContext ctx = new ImportParseService.ParseContext();
                ctx.bizCode = bizCode;
                ctx.masterFields = masterFields;
                ctx.detailTables = detailTables;
                ctx.errorCollector = collector;
                ctx.headerLabels = headerLabels;
                doc = parseService.parse(in, ctx);
                for (ParsedDocument.RowError e : doc.parseErrors) {
                    collector.add(e.rowNo, e.fieldName, e.reason);
                }
                log.info("[ImportPipeline] 解析完成: taskId={}, 主记录={}, 解析错误={}",
                        taskId, doc.records.size(), doc.parseErrors.size());
            }

            // ② 校验
            List<ParsedDocument.MasterRecord> valid = validateService.validate(meta, doc, collector);

            // ③ 反查（剔除反查失败记录）
            reverseResolver.resolve(bizCode, valid, collector);

            // 总数以解析出的主记录数为准（校验/反查剔除的计入失败而非凭空消失）
            long totalRecords = doc.records.size();
            long skipped = totalRecords - valid.size();

            // ④ 分批写入（批间检查取消 + 节流进度）
            List<MasterDetailWriter.DetailTarget> targets =
                    MasterDetailWriter.toDetailTargets(detailTables, keyExprByTable);
            long success = 0;
            long failed = skipped;
            String lastBatchError = null;
            int batchSize = properties.getBatchSize();
            for (int from = 0; from < valid.size(); from += batchSize) {
                if (taskService.isCancelRequested(taskId)) {
                    taskService.finish(taskId, ImportTaskService.STATUS_CANCELLED, "用户取消", null);
                    log.info("[ImportPipeline] 任务已取消: {}", taskId);
                    return;
                }
                int to = Math.min(valid.size(), from + batchSize);
                List<ParsedDocument.MasterRecord> batch = valid.subList(from, to);
                MasterDetailWriter.BatchResult br = masterDetailWriter.writeBatch(meta, targets, batch, taskId);
                success += br.successRecords;
                failed += br.failedRecords;
                if (br.error != null) {
                    lastBatchError = br.error;
                    for (ParsedDocument.MasterRecord rec : batch) {
                        collector.add(rec.excelRowNo, "", "批写入失败: " + br.error);
                    }
                }
                taskService.updateProgress(taskId, totalRecords, to, success, failed);
            }

            // ⑤ 终态 + 错误报告
            String status;
            String errorMsg = null;
            if (failed == 0 && !collector.hasErrors()) {
                status = ImportTaskService.STATUS_SUCCESS;
            } else if (success > 0) {
                status = ImportTaskService.STATUS_PARTIAL;
                errorMsg = "成功 " + success + " 条，失败 " + failed + " 条"
                        + (lastBatchError != null ? "（最后批错误: " + lastBatchError + "）" : "");
            } else {
                status = ImportTaskService.STATUS_FAILED;
                errorMsg = "导入失败: " + (lastBatchError != null ? lastBatchError
                        : collector.messages(3).stream().reduce((a, b) -> a + "；" + b).orElse("未知错误"));
            }
            String errorFilePath = null;
            if (collector.hasErrors()) {
                byte[] errorFile = collector.buildErrorFile();
                if (errorFile != null) {
                    errorFilePath = fileStore.save(taskId + "-errors", "错误报告.xlsx",
                            new java.io.ByteArrayInputStream(errorFile));
                }
            }
            taskService.finish(taskId, status, errorMsg, errorFilePath);
            analyzeTables(meta, detailTables);
            log.info("[ImportPipeline] 任务完成: taskId={}, status={}, success={}, failed={}, errors={}",
                    taskId, status, success, failed, collector.size());

        } catch (Exception e) {
            log.error("[ImportPipeline] 任务执行失败: taskId={}, {}", taskId, e.getMessage(), e);
            String errorFilePath = null;
            try {
                if (collector.hasErrors()) {
                    byte[] errorFile = collector.buildErrorFile();
                    if (errorFile != null) {
                        errorFilePath = fileStore.save(taskId + "-errors", "错误报告.xlsx",
                                new java.io.ByteArrayInputStream(errorFile));
                    }
                }
            } catch (Exception ignore) {
                // 错误文件生成失败不影响终态
            }
            taskService.finish(taskId, ImportTaskService.STATUS_FAILED,
                    "导入失败: " + e.getMessage(), errorFilePath);
        }
    }

    private String findFilePath(String taskId) {
        return Paths.get(properties.getFileDir(), taskId + ".xlsx").toAbsolutePath().toString();
    }

    /** 导入完成后刷新统计信息（仅 PG；大批量 INSERT 后统计过期会导致查询计划劣化，其他库静默跳过） */
    private void analyzeTables(BizMeta meta, List<Map.Entry<String, List<String>>> detailTables) {
        try {
            com.server.db.MultiDataSourceHolder.setDatasource("lowcode");
            org.springframework.jdbc.core.JdbcTemplate jdbc =
                    new org.springframework.jdbc.core.JdbcTemplate(dataSource);
            jdbc.execute("ANALYZE " + meta.getMainTable());
            for (Map.Entry<String, List<String>> dt : detailTables) {
                jdbc.execute("ANALYZE " + dt.getKey());
            }
            log.info("[ImportPipeline] 已刷新表统计信息: {}", meta.getMainTable());
        } catch (Exception e) {
            log.debug("[ImportPipeline] ANALYZE 跳过（非 PG 或权限不足）: {}", e.getMessage());
        } finally {
            com.server.db.MultiDataSourceHolder.clearDataSource();
        }
    }
}

