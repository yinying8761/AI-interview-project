package com.iflytek.interview.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能组卷结果主记录。
 */
@Data
@TableName("question_paper")
public class QuestionPaper {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long scenarioId;
    private String title;
    private String round;
    private Integer totalCount;
    private String strategyJson;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}