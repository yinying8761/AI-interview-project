package com.iflytek.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.interview.dto.*;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.entity.InterviewStatus;
import com.iflytek.interview.interview.entity.InterviewVideoEvent;
import com.iflytek.interview.interview.entity.InterviewVideoFrame;
import com.iflytek.interview.interview.entity.InterviewVideoSegment;
import com.iflytek.interview.interview.mapper.InterviewVideoEventMapper;
import com.iflytek.interview.interview.mapper.InterviewVideoFrameMapper;
import com.iflytek.interview.interview.mapper.InterviewVideoSegmentMapper;
import com.iflytek.interview.interview.service.InterviewRecordService;
import com.iflytek.interview.interview.service.InterviewVideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频采集服务实现。
 * 复用第05讲 InterviewRecordService.getSession 完成会话与角色校验；
 * 视频帧、视频片段均由本地 Mapper 直接读写，不依赖 MyBatis-Plus ServiceImpl。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewVideoServiceImpl implements InterviewVideoService {

    private final InterviewRecordService interviewRecordService;
    private final InterviewVideoFrameMapper frameMapper;
    private final InterviewVideoSegmentMapper segmentMapper;
    private final InterviewVideoEventMapper eventMapper;
    private final ObjectMapper objectMapper;

    /**
     * 抽帧后登记视频帧：校验请求、会话与采集人，计算帧序号后落库。
     */
    @Override
    @Transactional
    public InterviewVideoFrame createFrame(Long userId,
                                           VideoFrameCreateRequest request) {
        validateFrameRequest(request);

        // 复用第05讲 getSession：REST 线程具备 SecurityContext，可完成权限判断
        InterviewRecord record = interviewRecordService.getSession(
                request.getRecordId(), userId);

        ensureCapturable(record);
        ensureInterviewOperator(record, userId);

        // 计算同一题目下的下一帧序号；并发下建议使用 Redis INCR，此处用计数法并结合唯一键兜底
        Long frameSeq = frameMapper.selectCount(
                new LambdaQueryWrapper<InterviewVideoFrame>()
                        .eq(InterviewVideoFrame::getRecordId, record.getId())
                        .eq(InterviewVideoFrame::getQuestionIndex, request.getQuestionIndex())
        ) + 1;

        InterviewVideoFrame frame = new InterviewVideoFrame();
        frame.setRecordId(record.getId());
        frame.setUserId(userId);
        frame.setQuestionIndex(request.getQuestionIndex());
        frame.setFrameSeq(frameSeq);
        frame.setKeyFrame(Boolean.TRUE.equals(request.getKeyFrame()) ? 1 : 0);
        frame.setTimestampMs(request.getTimestampMs());
        frame.setCapturedAt(LocalDateTime.now());
        frame.setWidth(request.getWidth());
        frame.setHeight(request.getHeight());
        frame.setBrightness(request.getBrightness());
        frame.setSharpness(request.getSharpness());
        frame.setDiffRatio(request.getDiffRatio());
        frame.setMimeType(request.getMimeType() == null
                ? "image/jpeg" : request.getMimeType());
        frame.setStatus("CREATED");

        frameMapper.insert(frame);
        saveFrameEvent(frame, userId, "VIDEO_FRAME_SAVED", request);

        return frame;
    }

    /**
     * 帧图像上传完成：回填文件地址与缩略图，状态推进为 UPLOADED。
     */
    @Override
    @Transactional
    public InterviewVideoFrame uploadFrame(Long userId,
                                           VideoFrameUploadRequest request) {
        InterviewVideoFrame frame = frameMapper.selectById(request.getFrameId());

        if (frame == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 仅采集人本人或管理员可操作
        if (!frame.getUserId().equals(userId) && !SecurityUtil.hasRole("admin")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        frame.setFileUrl(request.getFileUrl());
        frame.setThumbnailUrl(request.getThumbnailUrl());
        frame.setFileSize(request.getFileSize());
        frame.setStatus("UPLOADED");

        frameMapper.updateById(frame);
        saveFrameEvent(frame, userId, "VIDEO_KEY_FRAME_SELECTED", request);

        return frame;
    }

    /**
     * 开始录制时登记视频片段：校验请求、会话与采集人，计算片段序号后落库。
     */
    @Override
    @Transactional
    public InterviewVideoSegment createSegment(Long userId,
                                               VideoSegmentCreateRequest request) {
        validateSegmentRequest(request);

        InterviewRecord record = interviewRecordService.getSession(
                request.getRecordId(), userId);

        ensureCapturable(record);
        ensureInterviewOperator(record, userId);

        // 计算同一题目下的下一片段序号；并发下建议使用 Redis INCR
        Integer segmentSeq = segmentMapper.selectCount(
                new LambdaQueryWrapper<InterviewVideoSegment>()
                        .eq(InterviewVideoSegment::getRecordId, record.getId())
                        .eq(InterviewVideoSegment::getQuestionIndex, request.getQuestionIndex())
        ).intValue() + 1;

        InterviewVideoSegment segment = new InterviewVideoSegment();
        segment.setRecordId(record.getId());
        segment.setUserId(userId);
        segment.setQuestionIndex(request.getQuestionIndex());
        segment.setSegmentSeq(segmentSeq);
        segment.setStartAt(LocalDateTime.now());
        segment.setDurationMs(0);
        segment.setMimeType(request.getMimeType());
        segment.setWidth(request.getWidth());
        segment.setHeight(request.getHeight());
        segment.setFrameRate(request.getFrameRate());
        segment.setBitrate(request.getBitrate());
        segment.setNetworkLevel(request.getNetworkLevel());
        segment.setStatus("RECORDING");

        segmentMapper.insert(segment);
        saveSegmentEvent(segment, null, userId, "VIDEO_SEGMENT_CREATED", request);

        return segment;
    }

    /**
     * 视频片段上传完成：回填地址、封面与时长，状态推进为 UPLOADED。
     */
    @Override
    @Transactional
    public InterviewVideoSegment uploadSegment(Long userId,
                                               VideoSegmentUploadRequest request) {
        InterviewVideoSegment segment = segmentMapper.selectById(request.getSegmentId());

        if (segment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 仅采集人本人或管理员可操作
        if (!segment.getUserId().equals(userId) && !SecurityUtil.hasRole("admin")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        segment.setFileUrl(request.getFileUrl());
        segment.setThumbnailUrl(request.getThumbnailUrl());
        segment.setFileSize(request.getFileSize());
        segment.setDurationMs(request.getDurationMs());
        segment.setEndAt(LocalDateTime.now());
        segment.setStatus("UPLOADED");

        segmentMapper.updateById(segment);
        saveSegmentEvent(segment, null, userId, "VIDEO_SEGMENT_UPLOADED", request);

        return segment;
    }

    /**
     * 查询视频帧列表：先校验会话可见性，再按帧序号升序返回，可用题号筛选。
     */
    @Override
    public List<InterviewVideoFrame> listFrames(Long recordId,
                                                Long userId,
                                                Integer questionIndex) {
        // 复用 getSession 实现会话可见性校验，REST 线程具备 SecurityContext
        interviewRecordService.getSession(recordId, userId);

        return frameMapper.selectList(
                new LambdaQueryWrapper<InterviewVideoFrame>()
                        .eq(InterviewVideoFrame::getRecordId, recordId)
                        .eq(questionIndex != null,
                                InterviewVideoFrame::getQuestionIndex, questionIndex)
                        .orderByAsc(InterviewVideoFrame::getFrameSeq));
    }

    /**
     * 查询视频片段列表：先校验会话可见性，再按片段序号升序返回，可用题号筛选。
     */
    @Override
    public List<InterviewVideoSegment> listSegments(Long recordId,
                                                    Long userId,
                                                    Integer questionIndex) {
        interviewRecordService.getSession(recordId, userId);

        return segmentMapper.selectList(
                new LambdaQueryWrapper<InterviewVideoSegment>()
                        .eq(InterviewVideoSegment::getRecordId, recordId)
                        .eq(questionIndex != null,
                                InterviewVideoSegment::getQuestionIndex, questionIndex)
                        .orderByAsc(InterviewVideoSegment::getSegmentSeq));
    }

    /** 仅待开始或进行中的会话允许登记视频采集，已结束或已取消的会话不满足采集前置条件。 */
    private void ensureCapturable(InterviewRecord record) {
        if (!InterviewStatus.PENDING.getCode().equals(record.getStatus())
                && !InterviewStatus.ONGOING.getCode().equals(record.getStatus())) {
            throw new BusinessException(400, "会话未处于可采集状态");
        }
    }

    /** 采集人必须是候选人本人，或管理员 / 面试官。 */
    private void ensureInterviewOperator(InterviewRecord record, Long userId) {
        if (!record.getUserId().equals(userId)
                && !SecurityUtil.hasRole("admin")
                && !SecurityUtil.hasRole("interviewer")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /** 校验创建视频帧请求：必填项与参数完整性。 */
    private void validateFrameRequest(VideoFrameCreateRequest request) {
        if (request.getRecordId() == null
                || request.getQuestionIndex() == null
                || request.getTimestampMs() == null
                || request.getWidth() == null
                || request.getHeight() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    /** 校验创建视频片段请求：必填项与参数完整性。 */
    private void validateSegmentRequest(VideoSegmentCreateRequest request) {
        if (request.getRecordId() == null
                || request.getQuestionIndex() == null
                || request.getMimeType() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    /** 写一条视频帧事件，供回放与审计。 */
    private void saveFrameEvent(InterviewVideoFrame frame,
                                Long userId,
                                String eventType,
                                Object payload) {
        InterviewVideoEvent event = new InterviewVideoEvent();
        event.setRecordId(frame.getRecordId());
        event.setFrameId(frame.getId());
        event.setQuestionIndex(frame.getQuestionIndex());
        event.setEventType(eventType);
        event.setOperatorId(userId);
        event.setEventTime(LocalDateTime.now());
        event.setPayloadJson(toJson(payload));
        eventMapper.insert(event);
    }

    /** 写一条视频片段事件，供回放与审计。 */
    private void saveSegmentEvent(InterviewVideoSegment segment,
                                  Long frameId,
                                  Long userId,
                                  String eventType,
                                  Object payload) {
        InterviewVideoEvent event = new InterviewVideoEvent();
        event.setRecordId(segment.getRecordId());
        event.setSegmentId(segment.getId());
        event.setFrameId(frameId);
        event.setQuestionIndex(segment.getQuestionIndex());
        event.setEventType(eventType);
        event.setOperatorId(userId);
        event.setEventTime(LocalDateTime.now());
        event.setPayloadJson(toJson(payload));
        eventMapper.insert(event);
    }

    /** 将事件载荷序列化为 JSON；失败时降级为 null，避免影响主流程。 */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("视频事件载荷序列化失败", e);
            return null;
        }
    }
}
