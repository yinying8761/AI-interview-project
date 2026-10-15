package com.iflytek.interview.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.common.ai.AiApiClient;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.mapper.InterviewRecordMapper;
import com.iflytek.interview.interview.service.InterviewRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/interview-sessions/{recordId}/conversation")
@RequiredArgsConstructor
@PreAuthorize("hasRole('candidate') and hasAuthority('interview:start')")
public class AiConversationController {
    private final InterviewRecordService records;
    private final InterviewRecordMapper mapper;
    private final AiApiClient ai;
    private final ObjectMapper json;

    private InterviewRecord owned(Long id) {
        InterviewRecord record = records.getSession(id, SecurityUtil.getCurrentUserId());
        if (!record.getUserId().equals(SecurityUtil.getCurrentUserId())) throw new BusinessException(403, "仅本人可以作答");
        if (!"ongoing".equals(record.getStatus())) throw new BusinessException(409, "面试未在进行中");
        if (record.getAiSessionId() == null) throw new BusinessException(503, "AI服务未就绪，请检查服务后重新创建面试");
        return record;
    }

    @GetMapping
    public Result<Map<String, Object>> state(@PathVariable Long recordId) {
        return Result.success(ai.sessionState(owned(recordId).getAiSessionId()));
    }

    @PostMapping("/opening")
    public Result<Map<String, Object>> opening(@PathVariable Long recordId) {
        return Result.success(ai.generateOpening(owned(recordId).getAiSessionId()));
    }

    @PostMapping
    @Transactional
    public Result<Map<String, Object>> answer(@PathVariable Long recordId, @RequestBody Map<String, String> body) throws Exception {
        mapper.selectForUpdate(recordId);
        InterviewRecord record = owned(recordId);
        String text = body.getOrDefault("message", "").trim();
        String submitId = body.getOrDefault("submitId", "").trim();
        if (text.isEmpty() || text.length() > 5000 || submitId.isEmpty() || submitId.length() > 64)
            throw new BusinessException(400, "回答需为1至5000字符，submitId需为1至64字符");
        if (submitId.equals(record.getSubmitId())) return Result.success(Map.of("session", ai.sessionState(record.getAiSessionId())));
        if (record.getQuestionIndex() != null && record.getQuestionIndex() >= 12) throw new BusinessException(409, "已完成12题，请生成报告");
        Map<String, Object> response = ai.sendMessage(record.getAiSessionId(), text);
        Map<?, ?> state = (Map<?, ?>) response.get("session");
        List<?> transcript = (List<?>) state.get("transcript");
        List<Map<String, String>> answers = new ArrayList<>();
        String question = "";
        if (transcript != null) for (Object item : transcript) {
            if (!(item instanceof Map<?, ?> m)) continue;
            if ("assistant".equals(m.get("role"))) question = String.valueOf(m.get("content"));
            if ("user".equals(m.get("role"))) answers.add(Map.of("content", question, "answer", String.valueOf(m.get("content"))));
        }
        record.setAnswerData(json.writeValueAsString(Map.of("answers", answers)));
        record.setQuestionIndex(answers.size());
        record.setSubmitId(submitId);
        mapper.updateById(record);
        records.saveEvent(recordId, record.getUserId(), "AI_ANSWER_SAVED", json.writeValueAsString(Map.of("submitId", submitId, "answerCount", answers.size())));
        return Result.success(response);
    }
}
