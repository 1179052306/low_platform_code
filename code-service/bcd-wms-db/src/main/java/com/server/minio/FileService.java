package com.server.minio;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    /**
     * 上传文件到配置的默认桶
     */
    public void upload(MultipartFile file, String objectName) throws Exception {
        String bucketName = properties.getBucketName();

        log.info("Uploading file [{}] to bucket [{}]", objectName, bucketName);

        // 1. 确保桶存在
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                log.info("Bucket [{}] not found, creating...", bucketName);
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            // 如果创建桶失败，直接抛出，中断上传
            log.error("Failed to check or create bucket: {}", e.getMessage());
            throw new RuntimeException("Bucket operation failed: " + e.getMessage(), e);
        }

        // 2. 上传文件
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            log.info("Upload successful: {}", objectName);

        } catch (Exception e) {
            log.error("Upload failed for {}: {}", objectName, e.getMessage());
            // 上传失败直接抛出异常
            throw new RuntimeException("File upload failed: " + e.getMessage(), e);
        }

        // 3. 不再生成 URL，方法直接结束，代表成功
    }

    public void upload(MultipartFile file, String objectName, String bucketName) throws Exception {

        log.info("Uploading file [{}] to bucket [{}]", objectName, bucketName);

        // 1. 确保桶存在
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                log.info("Bucket [{}] not found, creating...", bucketName);
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            // 如果创建桶失败，直接抛出，中断上传
            log.error("Failed to check or create bucket: {}", e.getMessage());
            throw new RuntimeException("Bucket operation failed: " + e.getMessage(), e);
        }

        // 2. 上传文件
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            log.info("Upload successful: {}", objectName);

        } catch (Exception e) {
            log.error("Upload failed for {}: {}", objectName, e.getMessage());
            // 上传失败直接抛出异常
            throw new RuntimeException("File upload failed: " + e.getMessage(), e);
        }

        // 3. 不再生成 URL，方法直接结束，代表成功
    }
    /**
     * 下载/获取文件流 (可选)
     */
    public InputStream download(String objectName) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(properties.getBucketName())
                .object(objectName)
                .build());
    }
}