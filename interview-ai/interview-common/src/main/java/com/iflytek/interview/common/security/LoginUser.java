package com.iflytek.interview.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 当前登录用户（第9讲起：由 Redis 会话还原出的身份）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    private Long userId;

    private String username;

    /** 会话 key（JWT 里的 sessionId），用于登出/定位 Redis 会话 */
    private String sessionId;

    /** 角色编码列表，如 ["candidate"] */
    private List<String> roles;

    /** 权限编码列表，如 ["record:view", "report:view"] */
    private List<String> permissions;

    /** 便捷判断：是否拥有某个角色 */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
