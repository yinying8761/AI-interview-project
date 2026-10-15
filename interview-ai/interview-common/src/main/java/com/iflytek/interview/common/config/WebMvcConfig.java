package com.iflytek.interview.common.config;

import com.iflytek.interview.common.interceptor.LogInterceptor;
import com.iflytek.interview.common.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册拦截器
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

//    @Autowired
//    private LoginInterceptor loginInterceptor;

    @Autowired
    private LogInterceptor logInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
//        registry.addInterceptor(loginInterceptor)
//                .addPathPatterns("/api/**")                    // 拦截 /api/ 下所有请求
//                .excludePathPatterns("/api/users/register",    // 排除注册
//                        "/api/users/login");      // 排除登录

        registry.addInterceptor(logInterceptor)
                .addPathPatterns("/api/**");
    }
}