package com.iflytek.interview.interview.dto;

import lombok.Data;

@Data
public class InterviewSessionEventRequest {

    private String eventType;
    private String payloadJson;
}