package com.iflytek.interview.interview.service;

import com.iflytek.interview.common.dto.AsrResponse;

/**
 * 语音转写服务：把音频片段交给 Python 转写，后处理并回写。
 */
public interface TranscriptionService {

    /**
     * 转写并把结果回写。
     * @param recordId 面试记录 ID
     * @param questionIndex 题号
     * @param segmentId 音频片段 ID
     * @param audioBytes 音频字节
     * @return 转写结果
     */
    AsrResponse transcribeAndSave(Long recordId, Integer questionIndex, Long segmentId, byte[] audioBytes);
}
