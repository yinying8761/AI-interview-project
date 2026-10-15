package com.iflytek.interview.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 请求日志拦截器：
 * 1) 生成 requestId（请求追踪号），放进 MDC——本次请求的所有日志都会自动带上它
 * 2) 记录开始时间；afterCompletion 计算耗时、慢请求告警
 */
@Slf4j
@Component
public class LogInterceptor implements HandlerInterceptor {

    // 本次请求的追踪号 key（与 logback pattern 的 %X{requestId} 对应）
    public static final String REQUEST_ID = "requestId";

    // ThreadLocal：每个线程独立存储开始时间，并发安全，用完必须 remove
    private static final ThreadLocal<Long> START_TIME = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // 1. 记录开始时间
        START_TIME.set(System.currentTimeMillis());

        // 2. 生成请求追踪号并放进 MDC——此后本请求的日志都带 requestId
        MDC.put(REQUEST_ID,
                UUID.randomUUID().toString().replace("-", "").substring(0, 12));

        // 3. 记录请求信息
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String ip = getClientIp(request);

        log.info(">>> 请求开始: [{}] {}, IP={}", method, uri, ip);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) throws Exception {

        Long startTime = START_TIME.get();
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            log.info("<<< 请求结束: [{}] {}, 状态={}, 耗时={}ms",
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), duration);

            // 慢请求告警
            if (duration > 3000) {
                log.warn("⚠️ 慢请求告警: {} 耗时 {}ms",
                        request.getRequestURI(), duration);
            }
        }
        // 铁律：用完清空，防止线程池复用线程时残留
        START_TIME.remove();
        MDC.remove(REQUEST_ID);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        return (ip != null && !ip.isEmpty()) ? ip : request.getRemoteAddr();
    }
}