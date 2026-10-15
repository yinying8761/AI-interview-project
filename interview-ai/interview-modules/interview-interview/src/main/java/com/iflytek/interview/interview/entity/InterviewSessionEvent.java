package com.iflytek.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_session_event")
public class InterviewSessionEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recordId;
    private String eventType;
    private Long operatorId;
    private LocalDateTime eventTime;
    private String payloadJson;
    private LocalDateTime createdAt;
}