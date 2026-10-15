package com.iflytek.interview.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

/**
 * 登录校验拦截器（简易版）
 * 检查请求头是否携带 token；细节验签下一讲交给 JWT
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    // 白名单：不需要登录就能访问的路径
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/api/users/register",   // 注册
            "/api/users/login",      // 登录
            "/api/test"              // 测试接口
    );

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String uri = request.getRequestURI();

        // 1. 白名单放行
        for (String whitePath : WHITE_LIST) {
            if (uri.startsWith(whitePath)) {
                return true;
            }
        }

        // 2. 检查请求头 token
        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            log.warn("未携带token被拦截: {}", uri);
            response.setContentType("application/json;charset=utf-8");
            response.setStatus(401);
            response.getWriter().write(
                    "{\"code\":401,\"message\":\"未登录，请先登录\",\"data\":null}"
            );
            return false;  // 中断请求
        }

        // 3. 携带了 token，放行（真实身份验签下一讲做）
        log.info("请求通过登录校验: {}", uri);
        return true;
    }
}
