package com.iflytek.interview.system.controller;

import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.common.security.SessionService;
import com.iflytek.interview.system.dto.ChangePasswordDTO;
import com.iflytek.interview.system.dto.LoginDTO;
import com.iflytek.interview.system.dto.RegisterDTO;
import com.iflytek.interview.system.dto.UpdateProfileDTO;
import com.iflytek.interview.system.entity.User;
import com.iflytek.interview.system.service.AuthService;
import com.iflytek.interview.system.service.UserService;
import com.iflytek.interview.system.vo.LoginVO;
import com.iflytek.interview.system.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理", description = "注册、登录、个人信息")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private SessionService sessionService;

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "用户名/密码/邮箱注册，密码 BCrypt 加密")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        if (userService.existsByUsername(dto.getUsername())) {
            throw new BusinessException(ErrorCode.USER_EXISTS);
        }
        User user = userService.register(dto);
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return Result.success(vo);
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "登录成功返回 JWT")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @PreAuthorize("hasAuthority('user:view')")
    @GetMapping("/{id}")
    public Result<UserVO> user(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return Result.success(vo);
    }

    @GetMapping("/profile")
    public Result<UserVO> profile() {
        User user = userService.getById(SecurityUtil.getCurrentUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return Result.success(vo);
    }

    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody UpdateProfileDTO dto) {
        userService.updateProfile(SecurityUtil.getCurrentUserId(), dto);
        User user = userService.getById(SecurityUtil.getCurrentUserId());
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return Result.success(vo);
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(SecurityUtil.getCurrentUserId(), dto);
        return Result.success();
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        sessionService.removeSession(SecurityUtil.getLoginUser().getSessionId());
        return Result.success();
    }
}
