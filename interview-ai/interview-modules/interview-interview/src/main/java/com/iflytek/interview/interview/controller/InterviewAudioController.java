package com.iflytek.interview.interview.controller;

import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.interview.dto.AudioSegmentCreateRequest;
import com.iflytek.interview.interview.dto.AudioSegmentUploadRequest;
import com.iflytek.interview.interview.entity.InterviewAudioSegment;
import com.iflytek.interview.interview.service.InterviewAudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 音频采集控制器。
 * 统一响应体为 Result；权限通过 @PreAuthorize 使用既有权限码。
 */
@RestController
@RequestMapping("/api/interview-audio")
@RequiredArgsConstructor
public class InterviewAudioController {

    private final InterviewAudioService audioService;

    /** 登记片段开始，需要 interview:start 权限。 */
    @PreAuthorize("hasAuthority('interview:start')")
    @PostMapping("/segments")
    public Result<InterviewAudioSegment> createSegment(
            @RequestBody AudioSegmentCreateRequest request) {
        return Result.success(audioService.createSegment(
                SecurityUtil.getCurrentUserId(), request));
    }

    /** 登记上传完成，需要 interview:submit 权限。 */
    @PreAuthorize("hasAuthority('interview:submit')")
    @PostMapping("/segments/uploaded")
    public Result<InterviewAudioSegment> markUploaded(
            @RequestBody AudioSegmentUploadRequest request) {
        return Result.success(audioService.markUploaded(
                SecurityUtil.getCurrentUserId(), request));
    }

    /** 标记已分析，需要 interview:start 权限。 */
    @PreAuthorize("hasAuthority('interview:start')")
    @PostMapping("/segments/{segmentId}/analyzed")
    public Result<InterviewAudioSegment> markAnalyzed(
            @PathVariable Long segmentId) {
        return Result.success(audioService.markAnalyzed(
                segmentId, SecurityUtil.getCurrentUserId()));
    }

    /** 标记失败，失败原因可选，默认“录制失败”。 */
    @PreAuthorize("hasAuthority('interview:start')")
    @PostMapping("/segments/{segmentId}/failed")
    public Result<InterviewAudioSegment> markFailed(
            @PathVariable Long segmentId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? "录制失败" : body.get("reason");

        return Result.success(audioService.markFailed(
                segmentId, SecurityUtil.getCurrentUserId(), reason));
    }

    /** 查询片段列表，需要 record:view 权限。 */
    @PreAuthorize("hasAuthority('record:view')")
    @GetMapping("/records/{recordId}/segments")
    public Result<List<InterviewAudioSegment>> listSegments(
            @PathVariable Long recordId) {
        return Result.success(audioService.listByRecord(
                recordId, SecurityUtil.getCurrentUserId()));
    }
}
