package com.iflytek.interview.interview.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FormalInterviewCreateRequest {

    /** 目标候选人用户ID */
    private Long candidateId;
    private Long scenarioId;
    private Long paperId;

    /** 预约开始/结束时间，可选 */
    private LocalDateTime scheduledAt;
    private LocalDateTime plannedEndAt;

    private String metadataJson;
}
