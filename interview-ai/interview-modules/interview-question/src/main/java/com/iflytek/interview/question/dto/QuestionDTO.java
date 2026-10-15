package com.iflytek.interview.question.dto;

import lombok.Data;

import java.util.List;

@Data
public class QuestionDTO {
    private Long scenarioId;
    private String content;
    private String type;
    private Integer difficulty;
    private String expectedAnswer;
    private String keywords;

    /** 标签编码，例如 SPRING_BOOT、JAVA_BACKEND */
    private List<String> tags;
}