package com.iflytek.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("interview_record")
public class InterviewRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long scenarioId;
    private Long paperId;
    private Long interviewerId;
    private String roomCode;
    private String status;
    private BigDecimal score;
    private Integer duration;
    private String answerData;
    private String aiFeedback;
    private LocalDateTime scheduledAt;
    private LocalDateTime plannedEndAt;
    private Integer reminderSent;
    private Integer questionIndex;
    private String metadataJson;
    private String aiSessionId;
    private String currentPhase;
    private String submitId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String username;

    @TableField(exist = false)
    private String nickname;
}