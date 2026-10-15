package com.iflytek.interview.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 连接配置（绑定 minio.* 配置项）
 */
@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    private String endpoint;     // 服务地址：http://minio所在服务器ip:9000
    private String accessKey;    // 账号：minioadmin
    private String secretKey;    // 密码：minioadmin
    private String bucket;       // 桶：interviewai
}