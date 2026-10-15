package com.iflytek.interview.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resume_version")
public class ResumeVersion {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private Long userId;
    private Integer version;
    private String fileName;
    private String objectName;
    private Long fileSize;
    private String contentType;
    private String fileType;
    private String status;
    private LocalDateTime createdAt;
}
