package com.api.lowcode.dataimport;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据导入配置（前缀 lowcode.import）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lowcode.import")
public class ImportProperties {

    /** 同步/异步分流阈值（主记录数） */
    private int syncThresholdRows = 2000;

    /** 全局并发导入任务上限（超出排队） */
    private int maxConcurrent = 2;

    /** 每批主记录数（批事务粒度） */
    private int batchSize = 1000;

    /** batchInsert 每片行数（500 值/批 IN 分片对齐） */
    private int batchInsertShard = 500;

    /** 暂存文件目录 */
    private String fileDir = "data/import";

    /** 单文件行数上限 */
    private long maxFileRows = 200000;

    /** 上传文件大小上限（MB） */
    private int maxFileMb = 20;

    /** 终态文件保留天数 */
    private int fileRetainDays = 7;

    /** 同步模式事务超时（秒） */
    private int syncTimeoutSeconds = 120;

    /** 查重/反查 IN 分片大小 */
    private int inShardSize = 500;
}