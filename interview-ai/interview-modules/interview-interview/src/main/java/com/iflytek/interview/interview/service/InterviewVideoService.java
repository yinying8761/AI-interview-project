package com.iflytek.interview.interview.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iflytek.interview.interview.dto.*;
import com.iflytek.interview.interview.entity.InterviewVideoFrame;
import com.iflytek.interview.interview.entity.InterviewVideoSegment;

import java.util.List;

/**
 * 视频采集服务。
 * 负责视频帧与视频片段的创建、上传登记与查询，
 * 为第12讲面部表情分析、第14讲多模态同步提供结构化视频输入。
 */
public interface InterviewVideoService {

    /** 抽帧后登记一帧画面，状态为 CREATED */
    InterviewVideoFrame createFrame(Long userId, VideoFrameCreateRequest request);

    /** 帧图像上传完成后回填地址，状态置为 UPLOADED */
    InterviewVideoFrame uploadFrame(Long userId, VideoFrameUploadRequest request);

    /** 开始录制时登记视频片段，状态为 RECORDING */
    InterviewVideoSegment createSegment(Long userId, VideoSegmentCreateRequest request);

    /** 视频片段上传完成后回填地址与时长，状态置为 UPLOADED */
    InterviewVideoSegment uploadSegment(Long userId, VideoSegmentUploadRequest request);

    /** 查询指定会话（可按题号筛选）下的视频帧，按帧序号升序 */
    List<InterviewVideoFrame> listFrames(Long recordId, Long userId, Integer questionIndex);

    /** 查询指定会话（可按题号筛选）下的视频片段，按片段序号升序 */
    List<InterviewVideoSegment> listSegments(Long recordId, Long userId, Integer questionIndex);
}
