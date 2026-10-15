package com.iflytek.interview.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.security.JwtUtil;
import com.iflytek.interview.common.security.LoginUser;
import com.iflytek.interview.common.security.SessionService;
import com.iflytek.interview.system.dto.LoginDTO;
import com.iflytek.interview.system.entity.*;
import com.iflytek.interview.system.mapper.*;
import com.iflytek.interview.system.vo.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SessionService sessionService;

    public LoginVO login(LoginDTO loginDTO) {

        // 1. 根据用户名查用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, loginDTO.getUsername()));
        if (user == null) {
            // 不区分"用户不存在"，统一报"用户名或密码错误"（防账号枚举）
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        // 2. BCrypt 校验密码
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        return issueLogin(user);
    }

    // 生成统一登录结果
    public LoginVO issueLogin(User user) {

        List<String> roles = getRolesByUserId(user.getId());
        List<String> permissions = getPermissionsByRoles(roles);

        String sessionId = sessionService.createSession(
                new LoginUser(user.getId(), user.getUsername(), null, roles, permissions));
        String token = jwtUtil.generateToken(user.getId(), sessionId);

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUserId(user.getId());
        loginVO.setUsername(user.getUsername());
        loginVO.setRoles(roles);
        return loginVO;
    }

    /** 用户 → user_role → 角色编码列表 */
    private List<String> getRolesByUserId(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(Role::getCode).collect(Collectors.toList());
    }

    /** 角色 → role_permission → 权限编码（多个角色取并集） */
    private List<String> getPermissionsByRoles(List<String> roleCodes) {
        if (roleCodes.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = roleMapper.selectList(
                        new LambdaQueryWrapper<Role>().in(Role::getCode, roleCodes))
                .stream().map(Role::getId).collect(Collectors.toList());
        List<Long> permissionIds = rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<RolePermission>()
                                .in(RolePermission::getRoleId, roleIds))
                .stream().map(RolePermission::getPermissionId)
                .distinct()
                .collect(Collectors.toList());
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectBatchIds(permissionIds).stream()
                .map(Permission::getCode).collect(Collectors.toList());
    }
}
