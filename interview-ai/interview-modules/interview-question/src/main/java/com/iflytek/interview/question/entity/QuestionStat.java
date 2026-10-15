package com.iflytek.interview.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题目使用统计，用于降低重复出题概率。
 */
@Data
@TableName("question_stat")
public class QuestionStat {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long questionId;
    private Integer useCount;
    private LocalDateTime lastUsedAt;
    private LocalDateTime updatedAt;
}