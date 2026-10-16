package com.iflytek.interview.resume.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResumeDeliveryVO {

    private Long deliveryId;
    private Long resumeId;
    private Long versionId;
    private Long candidateId;
    private String candidateName;
    private Long interviewerId;
    private String interviewerName;
    private String fileName;
    private String message;
    private String status;
    private LocalDateTime sentAt;

    /** 简历详情（列表为空，仅详情接口填充） */
    private ResumeVO resume;
}
