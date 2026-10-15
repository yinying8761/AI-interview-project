package com.iflytek.interview.interview.controller;

import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.interview.service.InterviewVisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 面部表情与注意力分析控制器。
 * Java 将抽帧转发给 Python，实时指标和结束汇总按训练记录落在本地系统中。
 */
@RestController
@RequestMapping("/api/interview-vision")
@RequiredArgsConstructor
public class InterviewVisionController {

    private final InterviewVisionService visionService;

    @PreAuthorize("hasAuthority('interview:start')")
    @GetMapping("/records/{recordId}/context")
    public Result<Map<String, Object>> context(@PathVariable Long recordId) {
        return Result.success(visionService.getContext(
                recordId, SecurityUtil.getCurrentUserId()));
    }

    /** 上报一帧并返回实时指标。 */
    @PreAuthorize("hasAuthority('interview:start')")
    @PostMapping("/{sessionId}/frames")
    public Result<Map<String, Object>> submitFrame(
            @PathVariable String sessionId,
            @RequestParam Long recordId,
            @RequestParam Integer questionIndex,
            @RequestParam("image") MultipartFile image) throws IOException {
        return Result.success(visionService.submitFrame(
                recordId,
                SecurityUtil.getCurrentUserId(),
                questionIndex,
                sessionId,
                image.getBytes()));
    }

    /** 拉取实时指标。data 结构与 Python /metrics 一致：{metrics, vision}。 */
    @PreAuthorize("hasAuthority('record:view')")
    @GetMapping("/{sessionId}/metrics")
    public Result<Map<String, Object>> metrics(@PathVariable String sessionId) {
        return Result.success(visionService.getMetrics(
                sessionId, SecurityUtil.getCurrentUserId()));
    }

    /** 结束并返回 video_summary。 */
    @PreAuthorize("hasAuthority('interview:submit')")
    @PostMapping("/{sessionId}/finish")
    public Result<Map<String, Object>> finish(@PathVariable String sessionId) {
        Map<String, Object> summary = visionService.finishByAiSession(
                sessionId, SecurityUtil.getCurrentUserId());
        return Result.success(Map.of("video_summary", summary));
    }
}
