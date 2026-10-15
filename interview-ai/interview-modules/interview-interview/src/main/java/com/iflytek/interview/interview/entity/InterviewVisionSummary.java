package com.iflytek.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Python /finish 返回的视觉分析汇总。
 * payloadJson 保留原始汇总载荷，作为后续视觉维度评分的输入。
 */
@Data
@TableName("interview_vision_summary")
public class InterviewVisionSummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private String sessionId;

    private BigDecimal expressionScore;

    private BigDecimal attentionScore;

    private Boolean videoAvailable;

    private Integer faceFrames;

    private BigDecimal durationSeconds;

    private String payloadJson;

    private LocalDateTime createdAt;
}
