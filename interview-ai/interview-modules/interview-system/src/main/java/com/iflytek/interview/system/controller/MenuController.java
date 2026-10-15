package com.iflytek.interview.system.controller;

import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/menus")
public class MenuController {

    /**
     * 返回当前登录用户的权限编码列表（第9讲起：权限来自 Redis 会话还原出的身份，直接取 SecurityContext 即可）
     */
    @GetMapping("/permissions")
    public Result<List<String>> getMyPermissions() {
        return Result.success(SecurityUtil.getCurrentUserPermissions());
    }
}
