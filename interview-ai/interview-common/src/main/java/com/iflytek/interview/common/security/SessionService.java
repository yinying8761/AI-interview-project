package com.iflytek.interview.common.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * 认证会话管理（第9讲）：登录成功后把用户身份（含角色/权限）写进 Redis 会话，
 * JWT 只携带会话 key。之后的过滤器按会话 key 从 Redis 还原身份——
 * 好处是配合登出、改角色/权限能做到即时生效，而不是只能等 JWT 自己过期。
 *
 * key 约定：login:session:{sessionId}，value 为 LoginUser（JSON 序列化）。
 */
@Component
public class SessionService {

    private static final String PREFIX = "login:session:";
    private static final String USER_SESSION_PREFIX = "login:user:sessions:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${jwt.expiration}")
    private Long expiration;

    /** 创建会话，返回会话 key（JWT 里只放这个 key，不再放角色/权限） */
    public String createSession(LoginUser loginUser) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                PREFIX + sessionId, loginUser, Duration.ofMillis(expiration));
        String userSessionKey = USER_SESSION_PREFIX + loginUser.getUserId();
        redisTemplate.opsForSet().add(userSessionKey, sessionId);
        redisTemplate.expire(userSessionKey, Duration.ofMillis(expiration));
        return sessionId;
    }

    /** 按会话 key 还原身份；会话不存在/已过期（登出/被删）返回 null */
    public LoginUser getSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        Object value = redisTemplate.opsForValue().get(PREFIX + sessionId);
        return value instanceof LoginUser loginUser ? loginUser : null;
    }

    /** 删除会话（登出/强制下线时调用），删除后该会话 key 对应的请求将 401 */
    public void removeSession(String sessionId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            Object value = redisTemplate.opsForValue().get(PREFIX + sessionId);
            if (value instanceof LoginUser loginUser) {
                redisTemplate.opsForSet().remove(
                        USER_SESSION_PREFIX + loginUser.getUserId(), sessionId);
            }
            redisTemplate.delete(PREFIX + sessionId);
        }
    }

    /** 强制清除指定用户的全部登录会话。 */
    public void removeUserSessions(Long userId) {
        if (userId == null) {
            return;
        }
        String userSessionKey = USER_SESSION_PREFIX + userId;
        Set<Object> sessionIds = redisTemplate.opsForSet().members(userSessionKey);
        if (sessionIds != null) {
            sessionIds.forEach(sessionId ->
                    redisTemplate.delete(PREFIX + String.valueOf(sessionId)));
        }
        redisTemplate.delete(userSessionKey);
    }
}
