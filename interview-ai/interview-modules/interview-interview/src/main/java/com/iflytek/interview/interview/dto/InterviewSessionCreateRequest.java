package com.iflytek.interview.interview.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InterviewSessionCreateRequest {

    private Long scenarioId;
    private Long paperId;

    private LocalDateTime scheduledAt;
    private LocalDateTime plannedEndAt;
    private String metadataJson;
}