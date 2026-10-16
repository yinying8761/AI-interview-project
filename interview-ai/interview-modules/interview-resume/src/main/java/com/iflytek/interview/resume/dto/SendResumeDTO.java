package com.iflytek.interview.resume.dto;

import lombok.Data;

@Data
public class SendResumeDTO {

    /** 接收简历的面试官用户ID */
    private Long interviewerId;

    /** 可选留言备注 */
    private String message;
}
