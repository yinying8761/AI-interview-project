package com.iflytek.interview.system.dto;

import lombok.Data;

@Data
public class UpdateProfileDTO {
    private String email;
    private String phone;
    private String nickname;
    private String avatar;
}
