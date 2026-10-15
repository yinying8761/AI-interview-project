package com.iflytek.interview.interview.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.interview.dto.InterviewSessionCreateRequest;
import com.iflytek.interview.interview.dto.InterviewSessionEventRequest;
import com.iflytek.interview.interview.dto.InterviewSessionScheduleRequest;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.entity.InterviewSessionEvent;
import com.iflytek.interview.interview.vo.InterviewStateSnapshot;
import com.iflytek.interview.interview.mapper.InterviewSessionEventMapper;
import com.iflytek.interview.interview.service.InterviewRecordService;
import com.iflytek.interview.interview.service.InterviewStateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview-sessions")
public class InterviewSessionController {

    private final InterviewRecordService sessionService;
    private final InterviewSessionEventMapper eventMapper;
    private final InterviewStateService stateService;

    public InterviewSessionController(InterviewRecordService sessionService,
                                      InterviewSessionEventMapper eventMapper,
                                      InterviewStateService stateService) {
        this.sessionService = sessionService;
        this.eventMapper = eventMapper;
        this.stateService = stateService;
    }

    @PreAuthorize("hasAuthority('record:create')")
    @PostMapping
    public Result<InterviewRecord> create(@RequestBody InterviewSessionCreateRequest request) {
        return Result.success(sessionService.createSession(
                SecurityUtil.getCurrentUserId(), request));
    }

    @PreAuthorize("hasAuthority('record:view')")
    @GetMapping
    public Result<List<InterviewRecord>> list() {
        return Result.success(sessionService.listForCurrentUser());
    }

    @PreAuthorize("hasAuthority('record:view')")
    @GetMapping("/{recordId}")
    public Result<InterviewRecord> detail(@PathVariable Long recordId) {
        return Result.success(sessionService.getSession(recordId, SecurityUtil.getCurrentUserId()));
    }

    /** 读取面试状态快照，供前端刷新 / 断线恢复。 */
    @PreAuthorize("hasAuthority('record:view')")
    @GetMapping("/{recordId}/state")
    public Result<InterviewStateSnapshot> state(@PathVariable Long recordId) {
        return Result.success(stateService.snapshot(recordId));
    }

    @PreAuthorize("hasAuthority('record:view')")
    @GetMapping("/{recordId}/events")
    public Result<List<InterviewSessionEvent>> events(@PathVariable Long recordId) {
        sessionService.getSession(recordId, SecurityUtil.getCurrentUserId());
        return Result.success(eventMapper.selectList(
                new LambdaQueryWrapper<InterviewSessionEvent>()
                        .eq(InterviewSessionEvent::getRecordId, recordId)
                        .orderByAsc(InterviewSessionEvent::getEventTime)));
    }

    @PreAuthorize("hasAuthority('interview:start')")
    @PostMapping("/{recordId}/schedule")
    public Result<InterviewRecord> schedule(
            @PathVariable Long recordId,
            @RequestBody InterviewSessionScheduleRequest request) {
        return Result.success(sessionService.schedule(
                recordId, SecurityUtil.getCurrentUserId(), request));
    }

    @PreAuthorize("hasAuthority('interview:start')")
    @PostMapping("/{recordId}/join")
    public Result<InterviewRecord> join(@PathVariable Long recordId,
                                        @RequestParam String role) {
        return Result.success(sessionService.join(
                recordId, SecurityUtil.getCurrentUserId(), role));
    }

    @PreAuthorize("hasAuthority('interview:start')")
    @PostMapping("/{recordId}/start")
    public Result<InterviewRecord> start(@PathVariable Long recordId) {
        return Result.success(sessionService.startInterview(
                recordId, SecurityUtil.getCurrentUserId()));
    }

    @PreAuthorize("hasAuthority('interview:submit')")
    @PostMapping("/{recordId}/complete")
    public Result<InterviewRecord> complete(
            @PathVariable Long recordId,
            @RequestBody(required = false) Map<String, String> body) {
        String answerData = body == null ? null : body.get("answerData");
        return Result.success(sessionService.submitInterview(
                recordId, SecurityUtil.getCurrentUserId(), answerData));
    }

    @PreAuthorize("hasAuthority('interview:cancel')")
    @PostMapping("/{recordId}/cancel")
    public Result<Void> cancel(
            @PathVariable Long recordId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        sessionService.cancelInterview(
                recordId, SecurityUtil.getCurrentUserId(), reason);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('interview:start')")
    @PostMapping("/{recordId}/events")
    public Result<Void> appendEvent(
            @PathVariable Long recordId,
            @RequestBody InterviewSessionEventRequest request) {
        sessionService.getSession(recordId, SecurityUtil.getCurrentUserId());
        sessionService.saveEvent(
                recordId,
                SecurityUtil.getCurrentUserId(),
                request.getEventType(),
                request.getPayloadJson());
        return Result.success();
    }
}