package com.iflytek.interview.system.controller;

import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.system.dto.OAuthLoginExchangeDTO;
import com.iflytek.interview.system.vo.LoginVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/oauth")
public class OAuthLoginController {

    private static final String LOGIN_CODE_PREFIX = "oauth:login:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/login/exchange")
    public Result<LoginVO> exchange(@Valid @RequestBody OAuthLoginExchangeDTO dto) {
        String key = LOGIN_CODE_PREFIX + dto.getLoginCode();
        Object value = redisTemplate.opsForValue().get(key);

        if (!(value instanceof LoginVO loginVO)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        redisTemplate.delete(key);
        return Result.success(loginVO);
    }
}
