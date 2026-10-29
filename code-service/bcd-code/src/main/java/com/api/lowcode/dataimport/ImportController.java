package com.api.lowcode.dataimport;

import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.file.Files;

/**
 * 数据导入接口：模板下载 / 执行导入 / 任务进度 / 错误文件 / 取消 / 历史。
 * 错误全部透传前端（ResMsg.res=false + msg）。
 */
@Slf4j
@RestController
@RequestMapping("/api/lowcode/import")
public class ImportController {

    private final ImportTemplateService templateService;
    private final ImportTaskService taskService;
    private final ImportFileStore fileStore;
    private final ImportProperties properties;

    public ImportController(ImportTemplateService templateService,
                            ImportTaskService taskService,
                            ImportFileStore fileStore,
                            ImportProperties properties) {
        this.templateService = templateService;
        this.taskService = taskService;
        this.fileStore = fileStore;
        this.properties = properties;
    }

    /** 下载导入模板 */
    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@RequestParam String bizCode) {
        try {
            byte[] bytes = templateService.generate(bizCode);
            String fileName = URLEncoder.encode("导入模板-" + bizCode + ".xlsx", "UTF-8")
                    .replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bytes);
        } catch (Exception e) {
            log.error("[Import] 模板生成失败: bizCode={}, {}", bizCode, e.getMessage(), e);
            return ResponseEntity.status(500)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("模板生成失败: " + e.getMessage()).getBytes());
        }
    }

    /**
     * 执行导入：上传文件 → 三层准入 → 建任务（PENDING）。
     * P2 全部转异步（同步分流阈值在 P3 流水线接入后启用）。
     */
    @PostMapping("/execute")
    public ResMsg execute(@RequestParam("file") MultipartFile file,
                          @RequestParam String bizCode,
                          @RequestParam(required = false, defaultValue = "") String userId) {
        ResMsg r = new ResMsg();
        try {
            if (file == null || file.isEmpty()) {
                r.setRes(false);
                r.setMsg("上传文件为空");
                return r;
            }
            String name = file.getOriginalFilename();
            if (name == null || !name.toLowerCase().endsWith(".xlsx")) {
                r.setRes(false);
                r.setMsg("仅支持 .xlsx 格式文件");
                return r;
            }
            if (file.getSize() > properties.getMaxFileMb() * 1024L * 1024L) {
                r.setRes(false);
                r.setMsg("文件超过大小上限 " + properties.getMaxFileMb() + "MB");
                return r;
            }
            String taskId = java.util.UUID.randomUUID().toString().replace("-", "");
            String filePath = fileStore.save(taskId, name, file.getInputStream());
            JSONObject task = taskService.createTask(taskId, bizCode, name, filePath, userId);
            r.setData(task);
            return r;
        } catch (Exception e) {
            log.error("[Import] 创建导入任务失败: bizCode={}, {}", bizCode, e.getMessage(), e);
            r.setRes(false);
            r.setMsg(e.getMessage());
            return r;
        }
    }

    /** 任务进度 */
    @GetMapping("/task/{taskId}")
    public ResMsg task(@PathVariable String taskId) {
        ResMsg r = new ResMsg();
        try {
            r.setData(taskService.taskView(taskId));
        } catch (Exception e) {
            r.setRes(false);
            r.setMsg(e.getMessage());
        }
        return r;
    }

    /** 错误报告文件下载（归属校验） */
    @GetMapping("/task/{taskId}/errors")
    public ResponseEntity<byte[]> errors(@PathVariable String taskId, @RequestParam String userId) {
        try {
            JSONObject task = taskService.findById(taskId);
            if (task == null) {
                return ResponseEntity.status(404).contentType(MediaType.TEXT_PLAIN)
                        .body("任务不存在".getBytes());
            }
            String createdBy = task.getString("CREATED_BY");
            if (userId != null && !userId.isEmpty() && createdBy != null && !createdBy.isEmpty()
                    && !userId.equals(createdBy)) {
                return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN)
                        .body("无权下载该任务的错误文件".getBytes());
            }
            String errorFile = task.getString("ERROR_FILE_PATH");
            if (errorFile == null || errorFile.isEmpty()) {
                return ResponseEntity.status(404).contentType(MediaType.TEXT_PLAIN)
                        .body("该任务无错误报告文件".getBytes());
            }
            File f = fileStore.load(errorFile).toFile();
            if (!f.exists()) {
                return ResponseEntity.status(404).contentType(MediaType.TEXT_PLAIN)
                        .body("错误报告文件已过期清理".getBytes());
            }
            String fileName = URLEncoder.encode("导入错误报告-" + taskId + ".xlsx", "UTF-8")
                    .replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(Files.readAllBytes(f.toPath()));
        } catch (Exception e) {
            log.error("[Import] 错误文件下载失败: taskId={}, {}", taskId, e.getMessage(), e);
            return ResponseEntity.status(500).contentType(MediaType.TEXT_PLAIN)
                    .body(("下载失败: " + e.getMessage()).getBytes());
        }
    }

    /** 取消任务 */
    @PostMapping("/task/{taskId}/cancel")
    public ResMsg cancel(@PathVariable String taskId) {
        ResMsg r = new ResMsg();
        try {
            boolean ok = taskService.cancel(taskId);
            r.setData(new JSONObject().fluentPut("ok", ok));
            if (!ok) {
                r.setMsg("任务已结束，无法取消");
            }
        } catch (Exception e) {
            r.setRes(false);
            r.setMsg(e.getMessage());
        }
        return r;
    }

    /** 导入历史（按创建人过滤） */
    @GetMapping("/tasks")
    public ResMsg tasks(@RequestParam(required = false, defaultValue = "") String userId,
                        @RequestParam(required = false, defaultValue = "") String bizCode,
                        @RequestParam(required = false, defaultValue = "1") int pageNum,
                        @RequestParam(required = false, defaultValue = "10") int pageSize) {
        ResMsg r = new ResMsg();
        try {
            r.setData(taskService.listTasks(bizCode, userId, pageNum, pageSize));
        } catch (Exception e) {
            r.setRes(false);
            r.setMsg(e.getMessage());
        }
        return r;
    }
}