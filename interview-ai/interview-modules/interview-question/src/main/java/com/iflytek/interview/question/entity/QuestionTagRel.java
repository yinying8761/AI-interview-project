package com.iflytek.interview.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题目与标签的多对多关系。
 */
@Data
@TableName("question_tag_rel")
public class QuestionTagRel {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long questionId;
    private Long tagId;
    private LocalDateTime createdAt;
}