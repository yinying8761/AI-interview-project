package com.iflytek.interview.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j 接口文档配置（OpenAPI 信息 + JWT 认证说明）
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("多模态智能模拟面试评测平台 API")
                        .description("面向学生的模拟面试评测系统后端接口文档")
                        .version("1.0"))
                // 声明全局需要携带的 Bearer Token（配合第7讲 JWT）
                .addSecurityItem(new SecurityRequirement().addList("BearerToken"))
                .components(new Components().addSecuritySchemes("BearerToken",
                        new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("Bearer")
                                .bearerFormat("JWT")));
    }
}
