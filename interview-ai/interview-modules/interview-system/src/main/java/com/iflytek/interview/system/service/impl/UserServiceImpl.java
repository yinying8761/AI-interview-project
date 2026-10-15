package com.iflytek.interview.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.system.dto.ChangePasswordDTO;
import com.iflytek.interview.system.dto.RegisterDTO;
import com.iflytek.interview.system.dto.UpdateProfileDTO;
import com.iflytek.interview.system.entity.Role;
import com.iflytek.interview.system.entity.User;
import com.iflytek.interview.system.entity.UserRole;
import com.iflytek.interview.system.mapper.RoleMapper;
import com.iflytek.interview.system.mapper.UserMapper;
import com.iflytek.interview.system.mapper.UserRoleMapper;
import com.iflytek.interview.system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl
        extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public boolean existsByUsername(String username) {
        return lambdaQuery().eq(User::getUsername, username).count() > 0;
    }

    @Override
    public User getByUsername(String username) {
        return lambdaQuery().eq(User::getUsername, username).one();
    }

    @Override
    @Transactional
    public User register(RegisterDTO dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        user.setStatus(1);
        save(user);

        Role candidateRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, "candidate"));
        if (candidateRole != null) {
            UserRole userRole = new UserRole();
            userRole.setUserId(user.getId());
            userRole.setRoleId(candidateRole.getId());
            userRoleMapper.insert(userRole);
        }
        return user;
    }

    @Override
    @Transactional
    public void updateProfile(Long userId, UpdateProfileDTO dto) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setNickname(dto.getNickname());
        user.setAvatar(dto.getAvatar());
        updateById(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        updateById(user);
    }
}
