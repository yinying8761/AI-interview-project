package com.iflytek.interview.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resume_delivery")
public class ResumeDelivery {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private Long versionId;
    private Long candidateId;
    private Long interviewerId;
    private String message;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
