package com.iflytek.interview.question.dto;

import lombok.Data;

@Data
public class QuestionTagDTO {
    private String name;
    private String code;
    private String type;
    private Long parentId;
    private Integer sortOrder;
    private Integer status;
}