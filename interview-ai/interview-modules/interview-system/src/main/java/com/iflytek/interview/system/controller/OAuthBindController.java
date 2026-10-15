package com.iflytek.interview.system.controller;

import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.system.entity.OAuthBinding;
import com.iflytek.interview.system.service.OAuthUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/oauth")
public class OAuthBindController {

    private static final String BIND_TICKET_PREFIX = "oauth:bind:";
    private static final String BIND_COOKIE_NAME = "OAUTH_BIND_TICKET";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private OAuthUserService oAuthUserService;

    @GetMapping("/gitee/bind/status")
    public Result<Map<String, Object>> status() {
        Long userId = SecurityUtil.getCurrentUserId();
        OAuthBinding binding = oAuthUserService.getBinding("gitee", userId);

        return Result.success(Map.of(
                "bound", binding != null,
                "provider", "gitee",
                "providerUsername", binding == null ? "" : String.valueOf(binding.getProviderUsername())
        ));
    }

    @PostMapping("/gitee/bind/start")
    public Result<Map<String, Object>> start(HttpServletResponse response) {

        Long userId = SecurityUtil.getCurrentUserId();

        String bindTicket = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                BIND_TICKET_PREFIX + bindTicket,
                String.valueOf(userId),
                Duration.ofMinutes(5));

        ResponseCookie cookie = ResponseCookie
                .from(BIND_COOKIE_NAME, bindTicket)
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofMinutes(5))
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return Result.success(Map.of(
                "authorizeUrl", "/oauth2/authorization/gitee"
        ));
    }
}