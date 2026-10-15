package com.iflytek.interview.system.dto;

import lombok.Data;

@Data
public class AdminUserQueryDTO {

    private long page = 1;
    private long size = 10;
    private String keyword;
    private String roleCode;
    private Integer status;
}
