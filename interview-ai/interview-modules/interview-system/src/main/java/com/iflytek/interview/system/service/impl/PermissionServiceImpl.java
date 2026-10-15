package com.iflytek.interview.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.interview.system.entity.Permission;
import com.iflytek.interview.system.entity.Role;
import com.iflytek.interview.system.mapper.PermissionMapper;
import com.iflytek.interview.system.mapper.RoleMapper;
import com.iflytek.interview.system.service.PermissionService;
import com.iflytek.interview.system.service.RoleService;
import org.springframework.stereotype.Service;

@Service
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission> implements PermissionService {
}
