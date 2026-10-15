package com.iflytek.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 实时视觉指标采样点。
 * 仅保存 Python 分析后的轻量指标，不保存原始人脸图像。
 */
@Data
@TableName("interview_vision_metric")
public class InterviewVisionMetric {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private String sessionId;

    private Integer questionIndex;

    private LocalDateTime timestamp;

    private String expression;

    private BigDecimal expressionScore;

    private BigDecimal attentionScore;

    private Boolean faceDetected;

    private LocalDateTime createdAt;
}
