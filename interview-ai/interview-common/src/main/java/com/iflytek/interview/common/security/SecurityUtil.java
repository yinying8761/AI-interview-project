package com.iflytek.interview.common.security;

import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * 当前用户工具：从 SecurityContext 获取登录用户信息
 */
public class SecurityUtil {

    // 强制使用静态方法，构造器私有
    private SecurityUtil() {}

    public static LoginUser getLoginUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    public static Long getCurrentUserId() {
        return getLoginUser().getUserId();
    }

    public static String getCurrentUsername() {
        return getLoginUser().getUsername();
    }

    /** 当前用户的角色编码列表，如 ["candidate"] */
    public static List<String> getCurrentUserRoles() {
        return getLoginUser().getRoles();
    }

    /** 当前用户的权限编码列表，如 ["record:view", "report:view"] */
    public static List<String> getCurrentUserPermissions() {
        return getLoginUser().getPermissions();
    }

    /** 是否拥有某个角色（数据权限等场景用） */
    public static boolean hasRole(String role) {
        return getLoginUser().hasRole(role);
    }
}
