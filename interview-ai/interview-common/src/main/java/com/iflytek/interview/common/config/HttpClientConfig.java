package com.iflytek.interview.common.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 通用 HTTP 客户端配置。
 * 使用 RestTemplateBuilder 构建，自动注册 Jackson 消息转换器，
 * 便于向 Python AI 等服务发送 / 接收 JSON。
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // 连接 5s、读取 120s：Whisper CPU 转写可能耗时较长，读超时需覆盖单次识别。
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(120))
                .build();
    }
}
