package com.api.lowcode.dataimport;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 导入暂存文件存取 —— 一期本地目录实现（data/import/{taskId}.xlsx）。
 * 多实例部署时替换为共享存储实现，流水线代码零改动。
 */
@Slf4j
@Component
public class ImportFileStore {

    @Resource
    private ImportProperties properties;

    private Path dir() throws IOException {
        Path dir = Paths.get(properties.getFileDir());
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }

    /** 保存上传文件，返回绝对路径 */
    public String save(String taskId, String originalFilename, InputStream in) throws IOException {
        Path target = dir().resolve(taskId + ".xlsx");
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("[ImportFileStore] 已保存导入文件: {} (原文件名: {})", target, originalFilename);
        return target.toAbsolutePath().toString();
    }

    /** 读取任务文件 */
    public Path load(String filePath) {
        return Paths.get(filePath);
    }

    /** 删除文件（终态清理，不存在时静默） */
    public void deleteQuietly(String filePath) {
        if (filePath == null || filePath.isEmpty()) return;
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (Exception e) {
            log.warn("[ImportFileStore] 删除文件失败: {}: {}", filePath, e.getMessage());
        }
    }

    /** 清理超期的终态文件（调度器顺带调用） */
    public int cleanExpired() {
        int deleted = 0;
        try {
            File dir = dir().toFile();
            File[] files = dir.listFiles();
            if (files == null) return 0;
            long cutoff = System.currentTimeMillis() - properties.getFileRetainDays() * 24L * 3600 * 1000;
            for (File f : files) {
                if (f.isFile() && f.lastModified() < cutoff) {
                    if (f.delete()) deleted++;
                }
            }
        } catch (Exception e) {
            log.warn("[ImportFileStore] 清理过期文件失败: {}", e.getMessage());
        }
        return deleted;
    }
}