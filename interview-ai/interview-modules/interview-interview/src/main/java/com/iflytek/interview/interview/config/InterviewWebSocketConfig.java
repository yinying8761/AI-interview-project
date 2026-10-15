package com.iflytek.interview.interview.config;

import com.iflytek.interview.interview.handler.InterviewSignalingHandler;
import com.iflytek.interview.interview.interceptor.InterviewSignalingHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：注册信令处理器与握手认证拦截器。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class InterviewWebSocketConfig implements WebSocketConfigurer {

    private final InterviewSignalingHandler signalingHandler;
    private final InterviewSignalingHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // addHandler 绑定路径；addInterceptors 处理握手认证；setAllowedOriginPatterns 限制来源
        registry.addHandler(signalingHandler, "/ws/interview")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(
                        "http://localhost:5173",
                        "http://localhost:8080");
    }
}
