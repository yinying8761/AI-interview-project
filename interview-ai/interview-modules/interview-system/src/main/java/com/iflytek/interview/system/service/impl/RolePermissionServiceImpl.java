package com.iflytek.interview.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.interview.system.entity.Permission;
import com.iflytek.interview.system.entity.RolePermission;
import com.iflytek.interview.system.mapper.PermissionMapper;
import com.iflytek.interview.system.mapper.RolePermissionMapper;
import com.iflytek.interview.system.service.PermissionService;
import com.iflytek.interview.system.service.RolePermissionService;
import org.springframework.stereotype.Service;

@Service
public class RolePermissionServiceImpl extends ServiceImpl<RolePermissionMapper, RolePermission> implements RolePermissionService {
}
