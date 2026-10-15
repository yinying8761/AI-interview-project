package com.iflytek.interview.resume.vo;

import lombok.Data;

import java.util.List;

@Data
public class ResumeParseResult {
    private String name;
    private String phone;
    private String email;
    private List<String> educationList;
    private List<String> skills;
    private List<String> projects;
    private List<String> keywords;
}
