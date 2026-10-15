package com.iflytek.interview.interview.controller;

import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.interview.dto.CreateRecordDTO;
import com.iflytek.interview.interview.dto.RecordScoreDTO;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.service.InterviewRecordService;
import com.iflytek.interview.question.vo.QuestionVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/records")
public class InterviewRecordController {

    @Autowired
    private InterviewRecordService recordService;

    @PreAuthorize("hasRole('candidate') and !hasRole('admin') and hasAuthority('record:create')")
    @PostMapping
    public Result<InterviewRecord> create(@RequestBody CreateRecordDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(recordService.createRecord(userId, dto.getScenarioId(), dto.getPaperId()));
    }

    @PreAuthorize("hasRole('candidate') and !hasRole('admin') and hasAuthority('interview:start')")
    @PostMapping("/{id}/start")
    public Result<InterviewRecord> start(@PathVariable Long id) {
        return Result.success(recordService.startInterview(id, SecurityUtil.getCurrentUserId()));
    }

    /** 获取本次面试的答题题目：绑定试卷则取试卷题目，否则随机。 */
    @PreAuthorize("hasRole('candidate') and !hasRole('admin') and hasAuthority('interview:start')")
    @GetMapping("/{id}/questions")
    public Result<List<QuestionVO>> questions(@PathVariable Long id) {
        return Result.success(recordService.getRecordQuestions(id, SecurityUtil.getCurrentUserId()));
    }

    @PreAuthorize("hasRole('candidate') and !hasRole('admin') and hasAuthority('interview:submit')")
    @PostMapping("/{id}/submit")
    public Result<InterviewRecord> submit(@PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        return Result.success(recordService.submitInterview(
                id,
                SecurityUtil.getCurrentUserId(),
                body.get("answerData")
        ));
    }

    @PreAuthorize("hasRole('candidate') and !hasRole('admin') and hasAuthority('interview:cancel')")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        recordService.cancelInterview(id, SecurityUtil.getCurrentUserId());
        return Result.success();
    }

    @PreAuthorize("hasAuthority('record:score')")
    @PutMapping("/{id}/score")
    public Result<InterviewRecord> score(@PathVariable Long id,
                                         @Valid @RequestBody RecordScoreDTO dto) {
        return Result.success(recordService.scoreInterview(id, dto));
    }

    @PreAuthorize("hasAuthority('record:view')")
    @GetMapping
    public Result<List<InterviewRecord>> list() {
        return Result.success(recordService.listForCurrentUser());
    }

    @PreAuthorize("hasAuthority('record:view')")
    @GetMapping("/my")
    public Result<List<InterviewRecord>> my() {
        return Result.success(recordService.listForCurrentUser());
    }
}