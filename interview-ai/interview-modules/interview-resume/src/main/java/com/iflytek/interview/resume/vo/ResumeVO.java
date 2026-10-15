package com.iflytek.interview.resume.vo;

import lombok.Data;

@Data
public class ResumeVO {
    private Long resumeId;
    private Integer version;
    private String fileName;
    private String fileUrl;
    private ResumeParseResult parseResult;
}
