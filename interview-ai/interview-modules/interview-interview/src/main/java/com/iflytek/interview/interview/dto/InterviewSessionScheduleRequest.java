package com.iflytek.interview.interview.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InterviewSessionScheduleRequest {

    private LocalDateTime scheduledAt;
    private LocalDateTime plannedEndAt;
}