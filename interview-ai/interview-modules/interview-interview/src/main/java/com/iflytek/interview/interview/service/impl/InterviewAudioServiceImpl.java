package com.iflytek.interview.interview.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.interview.dto.AudioSegmentCreateRequest;
import com.iflytek.interview.interview.dto.AudioSegmentUploadRequest;
import com.iflytek.interview.interview.entity.InterviewAudioEvent;
import com.iflytek.interview.interview.entity.InterviewAudioSegment;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.mapper.InterviewAudioEventMapper;
import com.iflytek.interview.interview.mapper.InterviewAudioSegmentMapper;
import com.iflytek.interview.interview.service.InterviewAudioService;
import com.iflytek.interview.interview.service.InterviewRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 音频采集服务实现。
 * 复用第05讲 InterviewRecordService.getSession 完成会话与角色校验；
 * 该服务被 REST 控制层调用，请求线程具备 SecurityContext，因此可使用 SecurityUtil.hasRole。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewAudioServiceImpl
        extends ServiceImpl<InterviewAudioSegmentMapper, InterviewAudioSegment>
        implements InterviewAudioService {

    private final InterviewRecordService interviewRecordService;
    private final InterviewAudioEventMapper audioEventMapper;
    private final ObjectMapper objectMapper;

    /**
     * 登记片段开始：校验请求与会话后，计算下一片段序号并落库。
     */
    @Override
    @Transactional
    public InterviewAudioSegment createSegment(Long userId,
                                               AudioSegmentCreateRequest request) {
        validateCreateRequest(request);

        // 复用第05讲 getSession：REST 线程具备 SecurityContext，可完成候选人/主持人/管理员权限判断
        InterviewRecord record = interviewRecordService.getSession(
                request.getRecordId(), userId);

        // 采集人必须是候选人本人，或者管理员 / 面试官
        if (!record.getUserId().equals(userId)
                && !SecurityUtil.hasRole("admin")
                && !SecurityUtil.hasRole("interviewer")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 计算同一题目下的下一片段序号；并发下建议使用原子序号，此处保留计数法并结合唯一键兜底
        Integer nextSeq = getNextSegmentSeq(
                request.getRecordId(), request.getQuestionIndex());

        InterviewAudioSegment segment = new InterviewAudioSegment();
        segment.setRecordId(request.getRecordId());
        segment.setUserId(userId);
        segment.setQuestionIndex(request.getQuestionIndex());
        segment.setSegmentSeq(nextSeq);
        segment.setStartAt(LocalDateTime.now());
        segment.setDurationMs(0);
        segment.setMimeType(request.getMimeType());
        segment.setSampleRate(request.getSampleRate());
        segment.setChannels(request.getChannels() == null ? 1 : request.getChannels());
        segment.setStatus("RECORDING");

        save(segment);
        saveEvent(segment, userId, "AUDIO_SEGMENT_CREATED", request);

        log.info("创建音频片段: recordId={}, questionIndex={}, segmentSeq={}",
                segment.getRecordId(), segment.getQuestionIndex(), segment.getSegmentSeq());

        return segment;
    }

    /**
     * 文件上传完成：回填文件地址、时长与音量指标，状态推进为 UPLOADED。
     */
    @Override
    @Transactional
    public InterviewAudioSegment markUploaded(Long userId,
                                              AudioSegmentUploadRequest request) {
        InterviewAudioSegment segment = getById(request.getSegmentId());

        if (segment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 仅采集人本人或管理员可操作
        if (!segment.getUserId().equals(userId) && !SecurityUtil.hasRole("admin")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        segment.setFileUrl(request.getFileUrl());
        segment.setFileSize(request.getFileSize());
        segment.setDurationMs(request.getDurationMs());
        segment.setAvgVolume(request.getAvgVolume());
        segment.setMaxVolume(request.getMaxVolume());
        segment.setSilenceRatio(request.getSilenceRatio());
        segment.setEndAt(LocalDateTime.now());
        segment.setStatus("UPLOADED");

        updateById(segment);
        saveEvent(segment, userId, "AUDIO_SEGMENT_UPLOADED", request);

        return segment;
    }

    /**
     * 指标计算完成：将状态推进为 ANALYZED。
     * 注意：修正了讲义中 `new BusinessException(...)` 多出的右括号，并先校验片段归属。
     */
    @Override
    @Transactional
    public InterviewAudioSegment markAnalyzed(Long segmentId, Long userId) {
        InterviewAudioSegment segment = getOwnedSegment(segmentId, userId);

        // 失败片段不可被标记为已分析，避免状态回退
        if ("FAILED".equals(segment.getStatus())) {
            throw new BusinessException(400, "失败片段无法标记为已分析");
        }

        segment.setStatus("ANALYZED");
        updateById(segment);
        saveEvent(segment, userId, "AUDIO_SEGMENT_ANALYZED", null);

        return segment;
    }

    /**
     * 录制或上传失败：记录失败原因，状态置为 FAILED。
     */
    @Override
    @Transactional
    public InterviewAudioSegment markFailed(Long segmentId,
                                            Long userId,
                                            String reason) {
        InterviewAudioSegment segment = getOwnedSegment(segmentId, userId);

        segment.setStatus("FAILED");
        segment.setEndAt(LocalDateTime.now());
        updateById(segment);

        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason);
        saveEvent(segment, userId, "AUDIO_SEGMENT_FAILED", payload);

        return segment;
    }

    /**
     * 查询会话片段列表：先校验用户对该会话的访问权限，再按题号与序号排序。
     */
    @Override
    public List<InterviewAudioSegment> listByRecord(Long recordId, Long userId) {
        // 复用 getSession 实现会话可见性校验，REST 线程具备 SecurityContext
        InterviewRecord record = interviewRecordService.getSession(recordId, userId);

        return lambdaQuery()
                .eq(InterviewAudioSegment::getRecordId, record.getId())
                .orderByAsc(InterviewAudioSegment::getQuestionIndex)
                .orderByAsc(InterviewAudioSegment::getSegmentSeq)
                .list();
    }

    /** 校验片段归属：片段必须存在，且当前用户为采集人本人或管理员。 */
    private InterviewAudioSegment getOwnedSegment(Long segmentId, Long userId) {
        InterviewAudioSegment segment = getById(segmentId);

        if (segment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        if (!segment.getUserId().equals(userId) && !SecurityUtil.hasRole("admin")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return segment;
    }

    /** 计算同题下一片段序号：计数法在并发下可能碰撞，建议改用原子序号。 */
    private Integer getNextSegmentSeq(Long recordId, Integer questionIndex) {
        Long count = lambdaQuery()
                .eq(InterviewAudioSegment::getRecordId, recordId)
                .eq(InterviewAudioSegment::getQuestionIndex, questionIndex)
                .count();

        return count.intValue() + 1;
    }

    /** 请求参数校验：必填项与题号范围约束。 */
    private void validateCreateRequest(AudioSegmentCreateRequest request) {
        if (request.getRecordId() == null || request.getQuestionIndex() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        if (request.getQuestionIndex() < 1 || request.getQuestionIndex() > 50) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    /** 写一条音频采集事件，供后续回放与审计使用。 */
    private void saveEvent(InterviewAudioSegment segment,
                           Long userId,
                           String eventType,
                           Object payload) {
        InterviewAudioEvent event = new InterviewAudioEvent();
        event.setRecordId(segment.getRecordId());
        event.setSegmentId(segment.getId());
        event.setQuestionIndex(segment.getQuestionIndex());
        event.setEventType(eventType);
        event.setOperatorId(userId);
        event.setEventTime(LocalDateTime.now());

        if (payload != null) {
            try {
                event.setPayloadJson(objectMapper.writeValueAsString(payload));
            } catch (Exception e) {
                log.warn("音频事件载荷序列化失败", e);
            }
        }

        audioEventMapper.insert(event);
    }
}
