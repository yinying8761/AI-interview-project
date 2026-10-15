package com.iflytek.interview.interview.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iflytek.interview.interview.dto.AudioSegmentCreateRequest;
import com.iflytek.interview.interview.dto.AudioSegmentUploadRequest;
import com.iflytek.interview.interview.entity.InterviewAudioSegment;

import java.util.List;

/**
 * 音频采集服务：负责片段的创建、上传登记、分析、失败与查询。
 * 状态推进目标为 RECORDING -> UPLOADED -> ANALYZED，供后续转写与评分使用。
 */
public interface InterviewAudioService extends IService<InterviewAudioSegment> {

    /** 开始一段录音时登记片段，状态置为 RECORDING */
    InterviewAudioSegment createSegment(Long userId, AudioSegmentCreateRequest request);

    /** 音频文件上传到对象存储后，回填地址与音量指标，状态置为 UPLOADED */
    InterviewAudioSegment markUploaded(Long userId, AudioSegmentUploadRequest request);

    /** 音量与静音指标计算完成后，状态置为 ANALYZED */
    InterviewAudioSegment markAnalyzed(Long segmentId, Long userId);

    /** 录制或上传失败时，记录失败原因，状态置为 FAILED */
    InterviewAudioSegment markFailed(Long segmentId, Long userId, String reason);

    /** 查询指定会话下的全部片段，按题号与片段序号升序排列 */
    List<InterviewAudioSegment> listByRecord(Long recordId, Long userId);
}
