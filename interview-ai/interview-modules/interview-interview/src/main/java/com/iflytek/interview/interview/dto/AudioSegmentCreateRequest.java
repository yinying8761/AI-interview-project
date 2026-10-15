package com.iflytek.interview.interview.dto;

import lombok.Data;

/**
 * 创建音频片段请求。
 * 前端在开始录制某道题时调用，服务端据此登记一个状态为 RECORDING 的片段。
 */
@Data
public class AudioSegmentCreateRequest {

    /** 面试会话 ID，定位到 interview_record */
    private Long recordId;

    /** 题目顺序，与试卷或随机抽题中的 questionIndex 对应 */
    private Integer questionIndex;

    /** 音频格式，例如 audio/webm;codecs=opus */
    private String mimeType;

    /** 采样率，例如 48000 */
    private Integer sampleRate;

    /** 声道数，通常为 1 */
    private Integer channels;
}
