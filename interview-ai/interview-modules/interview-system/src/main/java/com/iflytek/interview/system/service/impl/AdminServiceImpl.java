package com.iflytek.interview.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.response.PageResult;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.common.security.SessionService;
import com.iflytek.interview.system.dto.AdminUpdateUserDTO;
import com.iflytek.interview.system.dto.AdminUserQueryDTO;
import com.iflytek.interview.system.dto.ResetPasswordDTO;
import com.iflytek.interview.system.dto.UserStatusDTO;
import com.iflytek.interview.system.entity.Role;
import com.iflytek.interview.system.entity.User;
import com.iflytek.interview.system.entity.UserRole;
import com.iflytek.interview.system.service.AdminService;
import com.iflytek.interview.system.service.RoleService;
import com.iflytek.interview.system.service.UserRoleService;
import com.iflytek.interview.system.service.UserService;
import com.iflytek.interview.system.vo.UserVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminServiceImpl implements AdminService {

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SessionService sessionService;

    @Override
    public PageResult<UserVO> listUsers(AdminUserQueryDTO query) {
        long page = Math.max(query.getPage(), 1);
        long size = Math.min(Math.max(query.getSize(), 1), 100);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword)
                    .or().like(User::getEmail, keyword));
        }
        if (query.getStatus() != null) {
            wrapper.eq(User::getStatus, query.getStatus());
        }
        if (query.getRoleCode() != null && !query.getRoleCode().isBlank()) {
            Role role = getRoleByCode(query.getRoleCode());
            if (role == null) {
                return new PageResult<>(List.of(), 0, page, size);
            }
            List<Long> userIds = userRoleService.list(
                            new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, role.getId()))
                    .stream().map(UserRole::getUserId).distinct().toList();
            if (userIds.isEmpty()) {
                return new PageResult<>(List.of(), 0, page, size);
            }
            wrapper.in(User::getId, userIds);
        }

        wrapper.orderByDesc(User::getCreatedAt);
        Page<User> userPage = userService.page(new Page<>(page, size), wrapper);
        return new PageResult<>(toUserVOList(userPage.getRecords()), userPage.getTotal(), page, size);
    }

    private List<UserVO> toUserVOList(List<User> users) {
        if (users.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = users.stream().map(User::getId).toList();
        List<UserRole> userRoles = userRoleService.list(
                new LambdaQueryWrapper<UserRole>().in(UserRole::getUserId, userIds));

        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .distinct()
                .toList();
        Map<Long, String> roleCodeMap = new HashMap<>();
        if (!roleIds.isEmpty()) {
            roleService.listByIds(roleIds)
                    .forEach(role -> roleCodeMap.put(role.getId(), role.getCode()));
        }

        Map<Long, List<String>> userRoleMap = new HashMap<>();
        for (UserRole userRole : userRoles) {
            String roleCode = roleCodeMap.get(userRole.getRoleId());
            if (roleCode != null) {
                userRoleMap.computeIfAbsent(userRole.getUserId(), key -> new ArrayList<>())
                        .add(roleCode);
            }
        }

        return users.stream().map(user -> {
            UserVO vo = new UserVO();
            BeanUtils.copyProperties(user, vo);
            vo.setRoles(userRoleMap.getOrDefault(user.getId(), List.of()));
            return vo;
        }).toList();
    }

    @Override
    public List<Role> listRoles() {
        return roleService.list();
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Long> distinctRoleIds = roleIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinctRoleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Role> targetRoles = roleService.listByIds(distinctRoleIds);
        if (targetRoles.size() != distinctRoleIds.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        boolean currentlyAdmin = hasUserRole(userId, "admin");
        boolean remainsAdmin = targetRoles.stream().anyMatch(role -> "admin".equals(role.getCode()));
        if (currentlyAdmin && !remainsAdmin && user.getStatus() != null && user.getStatus() == 1
                && countActiveAdmins() <= 1) {
            throw new BusinessException(400, "至少需要保留一个有效管理员");
        }

        userRoleService.remove(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));

        for (Long roleId : distinctRoleIds) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoleService.save(userRole);
        }
        sessionService.removeUserSessions(userId);
    }

    @Override
    @Transactional
    public void resetPassword(Long userId, ResetPasswordDTO dto) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userService.updateById(user);
        sessionService.removeUserSessions(userId);
    }

    @Override
    @Transactional
    public UserVO updateUser(Long userId, AdminUpdateUserDTO dto) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        userService.updateById(user);
        return toUserVOList(List.of(user)).get(0);
    }

    @Override
    @Transactional
    public void updateStatus(Long userId, UserStatusDTO dto) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (dto.getStatus() == 0) {
            if (userId.equals(SecurityUtil.getCurrentUserId())) {
                throw new BusinessException(400, "不能禁用当前登录账号");
            }
            if (hasUserRole(userId, "admin") && user.getStatus() != null && user.getStatus() == 1
                    && countActiveAdmins() <= 1) {
                throw new BusinessException(400, "至少需要保留一个有效管理员");
            }
        }
        user.setStatus(dto.getStatus());
        userService.updateById(user);
        if (dto.getStatus() == 0) {
            sessionService.removeUserSessions(userId);
        }
    }

    private Role getRoleByCode(String roleCode) {
        return roleService.getOne(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode), false);
    }

    private boolean hasUserRole(Long userId, String roleCode) {
        Role role = getRoleByCode(roleCode);
        if (role == null) {
            return false;
        }
        return userRoleService.count(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, role.getId())) > 0;
    }

    private long countActiveAdmins() {
        Role adminRole = getRoleByCode("admin");
        if (adminRole == null) {
            return 0;
        }
        List<Long> adminUserIds = userRoleService.list(
                        new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, adminRole.getId()))
                .stream().map(UserRole::getUserId).distinct().toList();
        if (adminUserIds.isEmpty()) {
            return 0;
        }
        return userService.count(new LambdaQueryWrapper<User>()
                .in(User::getId, adminUserIds)
                .eq(User::getStatus, 1));
    }
}
