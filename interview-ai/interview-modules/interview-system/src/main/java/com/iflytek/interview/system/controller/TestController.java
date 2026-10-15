package com.iflytek.interview.system.controller;

import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅开发环境（dev profile）的 JWT 演示接口。
 * 第9讲起 token 只携带 sessionId，这里演示编码/解码；
 * 生产环境（prod）不加载本控制器。
 */
@Profile("dev")
@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/jwt-demo")
    public Result<String> jwtDemo() {
        String token = jwtUtil.generateToken(1L, "demo-session-123");
        Claims claims = jwtUtil.parseToken(token);
        String out = "token=" + token + " 解析出 userId=" + claims.get("userId")
                + " sessionId=" + claims.get("sessionId");
        return Result.success(out);
    }
}
