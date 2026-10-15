package com.iflytek.interview.file.util;

import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.file.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * MinIO 操作工具类：上传 / 删除 / 预签名URL
 */
@Slf4j
@Component
public class MinioUtil {

    @Autowired
    private MinioProperties minioProperties;

    private MinioClient minioClient;

    // 单文件最大分片（MinIO SDK 要求 stream 指定 partSize）
    private static final long PART_SIZE = 10 * 1024 * 1024;  // 10MB

    @PostConstruct
    public void init() {
        this.minioClient = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
        // 桶不存在则创建
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(minioProperties.getBucket()).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(minioProperties.getBucket()).build());
                log.info("MinIO 已创建桶: {}", minioProperties.getBucket());
            }
        } catch (Exception e) {
            log.error("MinIO 初始化失败", e);
        }
    }

    /**
     * 上传文件
     * @param objectName  对象路径，如 resumes/1/uuid_简历.pdf
     * @param inputStream 文件流
     * @param contentType 文件类型，如 application/pdf
     */
    public void upload(String objectName, InputStream inputStream, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectName)
                    .stream(inputStream, -1, PART_SIZE)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("文件上传失败: {}", objectName, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    /**
     * 删除对象
     */
    public void delete(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("文件删除失败: {}", objectName, e);
            throw new BusinessException(ErrorCode.FILE_DELETE_FAILED);
        }
    }

    /**
     * 获取对象访问 URL（不带签名，需要桶是公开读；私有桶用预签名URL）
     */
    public String getUrl(String objectName) {
        return minioProperties.getEndpoint() + "/" + minioProperties.getBucket() + "/" + objectName;
    }

    /**
     * 获取预签名下载 URL（私有桶时给临时凭证，expiry 秒后失效）
     */
    public String getPresignedUrl(String objectName, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectName)
                    .method(Method.GET)
                    .expiry(expirySeconds)
                    .build());
        } catch (Exception e) {
            log.error("获取文件URL失败: {}", objectName, e);
            throw new BusinessException(ErrorCode.FILE_URL_FAILED);
        }
    }
}