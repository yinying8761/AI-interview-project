package com.iflytek.interview.interview.service;

import com.iflytek.interview.interview.entity.QuestionPhase;
import com.iflytek.interview.interview.vo.InterviewStateSnapshot;

/**
 * 面试过程状态服务。
 * 负责读取状态快照、推进题目阶段、保证提交幂等并记录审计事件。
 */
public interface InterviewStateService {

    /** 读取当前面试的状态快照，用于刷新 / 断线恢复。 */
    InterviewStateSnapshot snapshot(Long recordId);

    /** 将当前题目阶段推进到目标阶段，并记录审计事件。 */
    void transitPhase(Long recordId, QuestionPhase target);

    /** 尝试提交：以 submitId 幂等，已存在则返回 false，不重复调用 AI。 */
    boolean trySubmit(Long recordId, String submitId);

    /** 写一条面试状态事件，供审计与回放使用。 */
    void recordEvent(Long recordId, String eventType, String payloadJson);
}
