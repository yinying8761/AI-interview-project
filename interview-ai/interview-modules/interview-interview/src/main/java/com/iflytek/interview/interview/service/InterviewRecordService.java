package com.iflytek.interview.interview.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iflytek.interview.interview.dto.InterviewSessionCreateRequest;
import com.iflytek.interview.interview.dto.InterviewSessionScheduleRequest;
import com.iflytek.interview.interview.dto.RecordScoreDTO;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.question.vo.QuestionVO;

import java.util.List;

public interface InterviewRecordService extends IService<InterviewRecord> {

    InterviewRecord createRecord(Long userId, Long scenarioId, Long paperId);

    InterviewRecord startInterview(Long recordId, Long userId);

    InterviewRecord submitInterview(Long recordId, Long userId, String answerData);

    void cancelInterview(Long recordId, Long userId);

    void cancelInterview(Long recordId, Long userId, String reason);

    InterviewRecord scoreInterview(Long recordId, RecordScoreDTO dto);

    List<InterviewRecord> listForCurrentUser();

    /** 获取某条面试记录的答题题目：绑定试卷则取试卷题目，否则随机。 */
    List<QuestionVO> getRecordQuestions(Long recordId, Long userId);

    // ===== 第05讲：面试会话管理 =====

    /** 创建面试会话（预约、房间、主持人、事件）。 */
    InterviewRecord createSession(Long userId, InterviewSessionCreateRequest request);

    /** 预约/变更会话时间，触发冲突检测。 */
    InterviewRecord schedule(Long recordId, Long userId, InterviewSessionScheduleRequest request);

    /** 加入会话：按 HOST/CANDIDATE 校验并记录事件。 */
    InterviewRecord join(Long recordId, Long userId, String role);

    /** 查询会话详情（含权限校验）。 */
    InterviewRecord getSession(Long recordId, Long userId);

    /** 写入一条会话过程事件。 */
    void saveEvent(Long recordId, Long operatorId, String eventType, String payloadJson);
}