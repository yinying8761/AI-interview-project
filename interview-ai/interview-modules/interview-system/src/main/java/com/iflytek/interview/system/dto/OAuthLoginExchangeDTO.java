package com.iflytek.interview.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OAuthLoginExchangeDTO {

    @NotBlank(message = "登录交换码不能为空")
    private String loginCode;
}
