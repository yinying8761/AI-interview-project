package com.iflytek.interview.interview.service.impl;

import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.entity.InterviewSessionEvent;
import com.iflytek.interview.interview.entity.QuestionPhase;
import com.iflytek.interview.interview.mapper.InterviewRecordMapper;
import com.iflytek.interview.interview.mapper.InterviewSessionEventMapper;
import com.iflytek.interview.interview.service.InterviewStateService;
import com.iflytek.interview.interview.vo.InterviewStateSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 面试过程状态服务实现。
 * 状态以 MySQL 为权威，Redis 仅用于快速读取与幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewStateServiceImpl implements InterviewStateService {

    /** Redis 幂等键前缀，配合 submitId 使用。 */
    private static final String SUBMIT_KEY = "interview:submit:";

    /** Redis 阶段缓存键前缀。 */
    private static final String PHASE_KEY = "interview:phase:";

    private final InterviewRecordMapper recordMapper;
    private final InterviewSessionEventMapper eventMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public InterviewStateSnapshot snapshot(Long recordId) {
        InterviewRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        InterviewStateSnapshot snapshot = new InterviewStateSnapshot();
        snapshot.setRecordId(record.getId());
        snapshot.setStatus(record.getStatus());
        snapshot.setQuestionIndex(record.getQuestionIndex());
        // 从数据库读取阶段字段；为空时默认 ASKING，保证结构稳定。
        String phaseCode = record.getCurrentPhase();
        snapshot.setPhase(phaseCode == null ? QuestionPhase.ASKING : QuestionPhase.valueOf(phaseCode));
        snapshot.setAiSessionId(record.getAiSessionId());
        snapshot.setAudioState("IDLE");
        snapshot.setVideoState("IDLE");
        snapshot.setLastFailReason(record.getMetadataJson());
        return snapshot;
    }

    @Override
    @Transactional
    public void transitPhase(Long recordId, QuestionPhase target) {
        InterviewRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        record.setCurrentPhase(target.getCode());
        recordMapper.updateById(record);

        // 刷新 Redis 阶段缓存，便于前端快速读取。
        stringRedisTemplate.opsForValue().set(PHASE_KEY + recordId, target.getCode(), Duration.ofMinutes(10));

        recordEvent(recordId, "QUESTION_PHASE_CHANGED",
                "{\"phase\":\"" + target.getCode() + "\"}");
        log.info("题目阶段推进: recordId={}, phase={}", recordId, target.getCode());
    }

    @Override
    public boolean trySubmit(Long recordId, String submitId) {
        if (submitId == null || submitId.isBlank()) {
            throw new BusinessException(400, "submitId 不能为空");
        }

        // SETNX 语义：只有第一次写入成功返回 true，重复提交直接返回 false。
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(SUBMIT_KEY + submitId, String.valueOf(recordId), Duration.ofMinutes(30));
        boolean ok = Boolean.TRUE.equals(first);
        if (ok) {
            recordEvent(recordId, "ANSWER_SUBMITTED", "{\"submitId\":\"" + submitId + "\"}");
        } else {
            // 幂等命中：不重复调用 AI，返回现状即可。
            log.info("重复提交被拦截: recordId={}, submitId={}", recordId, submitId);
        }
        return ok;
    }

    @Override
    public void recordEvent(Long recordId, String eventType, String payloadJson) {
        InterviewSessionEvent event = new InterviewSessionEvent();
        event.setRecordId(recordId);
        event.setEventType(eventType);
        event.setPayloadJson(payloadJson);
        event.setEventTime(LocalDateTime.now());
        eventMapper.insert(event);
    }
}
