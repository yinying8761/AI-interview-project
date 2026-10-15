package com.iflytek.interview.resume.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResumeVersionVO {
    private Long versionId;
    private Integer version;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String fileType;
    private String status;
    private LocalDateTime createdAt;
    private String presignedUrl;
}
