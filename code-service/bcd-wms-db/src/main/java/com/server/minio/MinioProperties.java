package com.server.minio;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author lw
 * @date: 2026/3/11
 * @description:
 **/
@Data
@Component
@ConfigurationProperties(prefix = "spring.minio")
public class MinioProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private boolean secure = false;
    private boolean ignoreSsl = false;
}