package com.iflytek.interview.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度4-20个字符")
    private String username;

    /*
     * 登录只校验密码非空。
     * 密码复杂度（字母+数字）属于注册与改密环节的约束，
     * 不应放在登录上：否则种子/历史账号（如 123456）会被直接拒绝，
     * 正确与否交由认证逻辑判定。
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
