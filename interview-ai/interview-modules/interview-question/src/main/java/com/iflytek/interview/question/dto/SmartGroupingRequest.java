package com.iflytek.interview.question.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 智能组卷请求：指定场景、轮次、题目数量与策略约束。
 */
@Data
public class SmartGroupingRequest {

    private Long scenarioId;
    private Integer round;
    private Integer totalCount;
    private String title;

    /** 知识标签编码 */
    private List<String> knowledgeTags;

    /** 岗位标签编码 */
    private List<String> jobTags;

    /** 轮次标签编码 */
    private List<String> roundTags;

    /** 难度计划：key 为难度，value 为数量 */
    private Map<Integer, Integer> difficultyPlan;
}