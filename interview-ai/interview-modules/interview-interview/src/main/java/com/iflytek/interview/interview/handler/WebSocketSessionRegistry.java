package com.iflytek.interview.interview.handler;

import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话注册表：以会话 ID 为键保存所有在线的 WebSocketSession。
 * 供信令处理器按成员 sessionId 定位对端会话。
 */
public class WebSocketSessionRegistry {

    private static final Map<String, WebSocketSession> SESSIONS =
            new ConcurrentHashMap<>();

    private WebSocketSessionRegistry() {
    }

    /** 注册会话。 */
    public static void add(WebSocketSession session) {
        if (session != null && session.getId() != null) {
            SESSIONS.put(session.getId(), session);
        }
    }

    /** 按会话 ID 找会话。 */
    public static WebSocketSession find(String sessionId) {
        return SESSIONS.get(sessionId);
    }

    /** 移除会话；连接关闭时调用，避免内存泄漏。 */
    public static void remove(String sessionId) {
        if (sessionId != null) {
            SESSIONS.remove(sessionId);
        }
    }
}
