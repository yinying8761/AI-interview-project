package com.iflytek.interview.interview.service;

import java.util.Map;

/**
 * 面部表情与注意力分析服务。
 * Java 侧负责帧转发、指标查询与最终汇总落库。
 */
public interface InterviewVisionService {

    /** 按训练记录获取视觉分析会话上下文。 */
    Map<String, Object> getContext(Long recordId, Long userId);

    /** 将一帧画面转发给 Python，并返回实时指标。 */
    Map<String, Object> submitFrame(Long recordId, Long userId, Integer questionIndex,
                                    String aiSessionId, byte[] imageBytes);

    /** 按 Python 会话查询实时视觉指标。 */
    Map<String, Object> getMetrics(String aiSessionId, Long userId);

    /** 按 Python 会话获取最终视觉汇总并落库。 */
    Map<String, Object> finishByAiSession(String aiSessionId, Long userId);

    /** 按训练记录获取最终视觉汇总并落库；已存在时复用汇总结果。 */
    Map<String, Object> finishAndSave(Long recordId, Long userId);
}
