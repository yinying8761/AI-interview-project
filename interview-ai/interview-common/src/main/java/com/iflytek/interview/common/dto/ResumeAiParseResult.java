package com.iflytek.interview.common.dto;

import lombok.Data;
import java.util.List;

/**
 * Python /api/ai/resume/parse 返回的 parsed 字段映射。
 * 字段名与 Python 端 _normalize_resume_parse 保持一致。
 */
@Data
public class ResumeAiParseResult {
    private String name;
    private String phone;
    private String email;
    private List<String> education;
    private List<String> skills;
    private List<String> projects;
    private List<String> workExperience;
    private List<String> keywords;
    private String summary;
    private String expectedPosition;
    private Integer yearsOfExperience;
}