package com.iflytek.interview.common.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import com.iflytek.interview.common.ai.AiProperties;

import java.time.Duration;

/**
 * 通用 HTTP 客户端配置。
 * 使用 RestTemplateBuilder 构建，自动注册 Jackson 消息转换器，
 * 便于向 Python AI 等服务发送 / 接收 JSON。
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, AiProperties properties) {
        return builder
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeout()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeout()))
                .build();
    }
}
