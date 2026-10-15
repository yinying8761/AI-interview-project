package com.iflytek.interview.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scenarioId;
    private String content;
    private String type;
    private Integer difficulty;
    private String expectedAnswer;
    private String keywords;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
