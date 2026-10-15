package com.iflytek.interview.interview.controller;

import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.interview.dto.*;
import com.iflytek.interview.interview.entity.InterviewVideoFrame;
import com.iflytek.interview.interview.entity.InterviewVideoSegment;
import com.iflytek.interview.interview.service.InterviewVideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 视频采集控制器。
 * 统一响应体为 Result；权限通过 @PreAuthorize 绑定既有权限码。
 */
@RestController
@RequestMapping("/api/interview-video")
@RequiredArgsConstructor
public class InterviewVideoController {

    private final InterviewVideoService videoService;

    /** 登记视频帧，需要 interview:start 权限。 */
    @PreAuthorize("hasAuthority('interview:start')")
    @PostMapping("/frames")
    public Result<InterviewVideoFrame> createFrame(
            @RequestBody VideoFrameCreateRequest request) {
        return Result.success(videoService.createFrame(
                SecurityUtil.getCurrentUserId(), request));
    }

    /** 登记帧上传完成，需要 interview:submit 权限。 */
    @PreAuthorize("hasAuthority('interview:submit')")
    @PostMapping("/frames/uploaded")
    public Result<InterviewVideoFrame> uploadFrame(
            @RequestBody VideoFrameUploadRequest request) {
        return Result.success(videoService.uploadFrame(
                SecurityUtil.getCurrentUserId(), request));
    }

    /** 登记视频片段，需要 interview:start 权限。 */
    @PreAuthorize("hasAuthority('interview:start')")
    @PostMapping("/segments")
    public Result<InterviewVideoSegment> createSegment(
            @RequestBody VideoSegmentCreateRequest request) {
        return Result.success(videoService.createSegment(
                SecurityUtil.getCurrentUserId(), request));
    }

    /** 登记片段上传完成，需要 interview:submit 权限。 */
    @PreAuthorize("hasAuthority('interview:submit')")
    @PostMapping("/segments/uploaded")
    public Result<InterviewVideoSegment> uploadSegment(
            @RequestBody VideoSegmentUploadRequest request) {
        return Result.success(videoService.uploadSegment(
                SecurityUtil.getCurrentUserId(), request));
    }

    /** 查询视频帧列表，需要 record:view 权限，可按题号筛选。 */
    @PreAuthorize("hasAuthority('record:view')")
    @GetMapping("/records/{recordId}/frames")
    public Result<List<InterviewVideoFrame>> listFrames(
            @PathVariable Long recordId,
            @RequestParam(required = false) Integer questionIndex) {
        return Result.success(videoService.listFrames(
                recordId, SecurityUtil.getCurrentUserId(), questionIndex));
    }

    /** 查询视频片段列表，需要 record:view 权限，可按题号筛选。 */
    @PreAuthorize("hasAuthority('record:view')")
    @GetMapping("/records/{recordId}/segments")
    public Result<List<InterviewVideoSegment>> listSegments(
            @PathVariable Long recordId,
            @RequestParam(required = false) Integer questionIndex) {
        return Result.success(videoService.listSegments(
                recordId, SecurityUtil.getCurrentUserId(), questionIndex));
    }
}
