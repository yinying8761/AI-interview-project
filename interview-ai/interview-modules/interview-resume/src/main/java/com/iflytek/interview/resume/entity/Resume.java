package com.iflytek.interview.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resume")
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer currentVersion;
    private String parsedName;
    private String parsedPhone;
    private String parsedEmail;
    private String educationSummary;
    private String skillSummary;
    private String projectSummary;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
