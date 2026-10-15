package com.iflytek.interview.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.interview.system.entity.Role;
import com.iflytek.interview.system.entity.UserRole;
import com.iflytek.interview.system.mapper.RoleMapper;
import com.iflytek.interview.system.mapper.UserRoleMapper;
import com.iflytek.interview.system.service.RoleService;
import com.iflytek.interview.system.service.UserRoleService;
import org.springframework.stereotype.Service;

@Service
public class UserRoleServiceImpl extends ServiceImpl<UserRoleMapper, UserRole> implements UserRoleService {
}
