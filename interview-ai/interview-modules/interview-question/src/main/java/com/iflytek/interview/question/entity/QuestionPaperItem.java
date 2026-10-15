package com.iflytek.interview.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 试卷题目明细。
 */
@Data
@TableName("question_paper_item")
public class QuestionPaperItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private Long questionId;
    private Integer sortOrder;
    private Integer score;
    private LocalDateTime createdAt;
}