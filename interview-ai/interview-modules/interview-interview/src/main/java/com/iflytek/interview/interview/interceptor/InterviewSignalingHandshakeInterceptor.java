package com.iflytek.interview.interview.interceptor;

import com.iflytek.interview.common.security.LoginUser;
import com.iflytek.interview.common.security.SessionService;
import com.iflytek.interview.common.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手认证拦截器：从查询参数解析 token，还原登录用户并把 userId / username
 * 写入 attributes，供后续信令处理读取。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewSignalingHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;
    private final SessionService sessionService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        // 从 URI 查询串中取出 token
        String query = request.getURI().getQuery();
        String token = parseQuery(query, "token");

        if (token == null || token.isBlank()) {
            log.warn("WebSocket握手缺少token");
            return false;
        }

        try {
            // 解析 JWT 拿到 sessionId，再用该 key 从 Redis 还原登录用户
            Claims claims = jwtUtil.parseToken(token);
            String sessionId = claims.get("sessionId", String.class);
            LoginUser loginUser = sessionService.getSession(sessionId);

            if (loginUser == null) {
                log.warn("WebSocket握手会话不存在");
                return false;
            }

            // 把用户身份放进 attributes，后续 handleJoin 通过 session.getAttributes() 读取
            attributes.put("userId", loginUser.getUserId());
            attributes.put("username", loginUser.getUsername());
            attributes.put("roles", loginUser.getRoles());
            return true;
        } catch (Exception e) {
            log.warn("WebSocket握手认证失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // 握手后无需额外处理
    }

    /** 从查询串中按 key 解析参数值，例如 token=xxx 中的 xxx。 */
    private String parseQuery(String query, String key) {
        if (query == null || query.isBlank()) {
            return null;
        }

        for (String item : query.split("&")) {
            String[] pair = item.split("=", 2);
            if (pair.length == 2 && pair[0].equals(key)) {
                return pair[1];
            }
        }

        return null;
    }
}
