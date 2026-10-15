package com.iflytek.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.common.ai.AiApiClient;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.entity.InterviewVisionMetric;
import com.iflytek.interview.interview.entity.InterviewVisionSummary;
import com.iflytek.interview.interview.mapper.InterviewRecordMapper;
import com.iflytek.interview.interview.mapper.InterviewSessionEventMapper;
import com.iflytek.interview.interview.mapper.InterviewVisionMetricMapper;
import com.iflytek.interview.interview.mapper.InterviewVisionSummaryMapper;
import com.iflytek.interview.interview.service.InterviewVisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面部表情与注意力分析服务实现。
 * 实时指标按采样节奏写入 MySQL，汇总结果在结束时写入 MySQL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewVisionServiceImpl implements InterviewVisionService {

    private static final String METRIC_SAMPLE_KEY = "interview:vision:metric:%s";
    private static final Duration METRIC_SAMPLE_TTL = Duration.ofSeconds(2);

    /**
     * 完整 AI 评测报告在返回结果中的键。
     * Python /finish 的报告除 video_summary 外还包含 question_evaluation（逐题评估）
     * 与 score_fusion（问答分与融合公式），此前只取 video_summary 导致问答得分被丢弃，
     * 由写死分值顶替；现按此键一并向调用方透出，供 ReportGenerator 真实评分。
     */
    public static final String AI_REPORT_KEY = "ai_report";

    private final AiApiClient aiApiClient;
    private final InterviewRecordMapper recordMapper;
    private final InterviewVisionMetricMapper metricMapper;
    private final InterviewVisionSummaryMapper summaryMapper;
    private final InterviewSessionEventMapper sessionEventMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> getContext(Long recordId, Long userId) {
        InterviewRecord record = getRecord(recordId);
        ensureOperator(record, userId);
        requireSession(record);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recordId", record.getId());
        result.put("sessionId", record.getAiSessionId());
        result.put("questionIndex", record.getQuestionIndex() == null ? 1 : record.getQuestionIndex());
        result.put("status", record.getStatus());
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> submitFrame(Long recordId, Long userId,
                                           Integer questionIndex, String aiSessionId,
                                           byte[] imageBytes) {
        if (questionIndex == null || questionIndex < 1 || imageBytes == null || imageBytes.length < 32) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        InterviewRecord record = getRecord(recordId);
        ensureOperator(record, userId);
        requireSession(record);
        if (!record.getAiSessionId().equals(aiSessionId)) {
            // 客户端缓存的会话号可能已过期（如 Python 服务重启后自动重建），
            // 以训练记录保存的当前会话为准，响应中的 sessionId 会同步给前端。
            log.warn("客户端视觉会话号与记录不一致，按记录当前会话处理: recordId={}, client={}, current={}",
                    recordId, aiSessionId, record.getAiSessionId());
        }

        Map<String, Object> response;
        try {
            response = aiApiClient.submitFrame(record.getAiSessionId(), imageBytes);
        } catch (HttpClientErrorException.NotFound e) {
            // Python 会话保存在内存中，服务重启即失效；自动重建并重试一次。
            recreateAiSession(record, "frames 404: " + e.getMessage());
            try {
                response = aiApiClient.submitFrame(record.getAiSessionId(), imageBytes);
            } catch (HttpClientErrorException.NotFound retryError) {
                log.warn("重建后视觉会话仍不可用: recordId={}", record.getId());
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("accepted", false);
                empty.put("metrics", new LinkedHashMap<>());
                empty.put("vision", Map.of());
                empty.put("sessionId", record.getAiSessionId());
                return empty;
            }
        }
        logVisionHealth(record, response);
        Map<String, Object> metrics = extractMetrics(response);
        saveMetricSample(record, questionIndex, metrics);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", response.getOrDefault("accepted", true));
        result.put("replaced_pending", response.getOrDefault("replaced_pending", false));
        result.put("metrics", metrics);
        // 视觉工作线程状态透传给前端，用于展示分析链路、采样速率与延迟。
        Map<String, Object> vision = mapValue(response, "vision");
        result.put("vision", vision == null ? Map.of() : vision);
        result.put("sessionId", record.getAiSessionId());
        return result;
    }

    @Override
    public Map<String, Object> getMetrics(String aiSessionId, Long userId) {
        InterviewRecord record = getRecordBySession(aiSessionId, userId);
        Map<String, Object> response;
        try {
            response = aiApiClient.getMetrics(record.getAiSessionId());
        } catch (HttpClientErrorException.NotFound e) {
            // Python 会话保存在内存中，服务重启即失效；自动重建并重试一次。
            recreateAiSession(record, "metrics 404: " + e.getMessage());
            try {
                response = aiApiClient.getMetrics(record.getAiSessionId());
            } catch (HttpClientErrorException.NotFound retryError) {
                response = Map.of();
            }
        }
        logVisionHealth(record, response);
        // 与 Python /metrics 的响应结构保持一致：{"metrics": 实时指标, "vision": 线程状态}。
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metrics", extractMetrics(response));
        Map<String, Object> vision = mapValue(response, "vision");
        result.put("vision", vision == null ? Map.of() : vision);
        result.put("sessionId", record.getAiSessionId());
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> finishByAiSession(String aiSessionId, Long userId) {
        InterviewRecord record = getRecordBySession(aiSessionId, userId);
        return finishAndSave(record, userId);
    }

    @Override
    @Transactional
    public Map<String, Object> finishAndSave(Long recordId, Long userId) {
        return finishAndSave(getRecord(recordId), userId);
    }

    private Map<String, Object> finishAndSave(InterviewRecord record, Long userId) {
        ensureOperator(record, userId);

        InterviewVisionSummary existing = findSummary(record.getId());
        if (existing != null) {
            return fromExistingSummary(existing);
        }

        Map<String, Object> summary;
        Map<String, Object> aiReport = null;
        boolean analysisAvailable = true;
        try {
            aiReport = aiApiClient.finish(record.getAiSessionId());
            summary = extractVideoSummary(aiReport);
            if (summary == null || summary.isEmpty()) {
                summary = unavailableSummary();
                analysisAvailable = false;
            }
        } catch (Exception e) {
            log.warn("视觉分析汇总失败，按视觉维度不可用的结果落库: recordId={}, aiSessionId={}, err={}",
                    record.getId(), record.getAiSessionId(), e.getMessage());
            summary = unavailableSummary();
            analysisAvailable = false;
        }

        persistSummary(record, summary);
        saveEvent(record, userId,
                analysisAvailable ? "VISION_SUMMARY_SAVED" : "VISION_SUMMARY_UNAVAILABLE",
                summary);

        if (aiReport == null) {
            return summary;
        }
        // 附带完整报告：视频维度字段保持在顶层（调用方与接口契约不变），
        // 问答评估与融合分放在 ai_report 键下。
        Map<String, Object> result = new LinkedHashMap<>(summary);
        result.put(AI_REPORT_KEY, aiReport);
        return result;
    }

    private void saveMetricSample(InterviewRecord record, Integer questionIndex,
                                  Map<String, Object> metrics) {
        String sampleKey = String.format(METRIC_SAMPLE_KEY, record.getId());
        Boolean sampled = stringRedisTemplate.opsForValue()
                .setIfAbsent(sampleKey, String.valueOf(LocalDateTime.now()), METRIC_SAMPLE_TTL);
        if (!Boolean.TRUE.equals(sampled)) {
            return;
        }

        InterviewVisionMetric metric = new InterviewVisionMetric();
        metric.setRecordId(record.getId());
        metric.setSessionId(record.getAiSessionId());
        metric.setQuestionIndex(questionIndex);
        metric.setTimestamp(LocalDateTime.now());
        metric.setExpression(asString(first(metrics, "expression", "emotion", "expression_label")));
        metric.setExpressionScore(asDecimal(first(metrics, "expression_score", "emotion_score")));
        metric.setAttentionScore(asDecimal(first(metrics, "attention_score")));
        metric.setFaceDetected(asBoolean(first(metrics, "face_detected", "face_present")));
        metricMapper.insert(metric);
    }

    private void persistSummary(InterviewRecord record, Map<String, Object> summary) {
        if (findSummary(record.getId()) != null) {
            return;
        }

        InterviewVisionSummary visionSummary = new InterviewVisionSummary();
        visionSummary.setRecordId(record.getId());
        visionSummary.setSessionId(visionSessionId(record));
        visionSummary.setExpressionScore(asDecimal(first(summary, "expression_score")));
        visionSummary.setAttentionScore(asDecimal(first(summary, "attention_score")));
        visionSummary.setVideoAvailable(asBoolean(first(summary, "video_available", "available")));
        visionSummary.setFaceFrames(asInteger(first(summary, "face_frames", "valid_face_frames")));
        visionSummary.setDurationSeconds(asDecimal(first(summary, "duration_seconds", "duration")));
        try {
            visionSummary.setPayloadJson(objectMapper.writeValueAsString(summary));
        } catch (Exception e) {
            log.warn("视觉汇总载荷序列化失败: recordId={}", record.getId(), e);
            visionSummary.setPayloadJson("{}");
        }
        summaryMapper.insert(visionSummary);
    }

    private InterviewVisionSummary findSummary(Long recordId) {
        return summaryMapper.selectOne(new LambdaQueryWrapper<InterviewVisionSummary>()
                .eq(InterviewVisionSummary::getRecordId, recordId)
                .last("limit 1"));
    }

    private Map<String, Object> fromExistingSummary(InterviewVisionSummary summary) {
        try {
            return objectMapper.readValue(summary.getPayloadJson(), new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("视觉汇总载荷读取失败，返回结构化冗余字段: summaryId={}", summary.getId(), e);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("expression_score", summary.getExpressionScore());
            result.put("attention_score", summary.getAttentionScore());
            result.put("video_available", summary.getVideoAvailable());
            result.put("face_frames", summary.getFaceFrames());
            result.put("duration_seconds", summary.getDurationSeconds());
            return result;
        }
    }

    private Map<String, Object> unavailableSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("expression_score", null);
        summary.put("attention_score", null);
        summary.put("video_available", false);
        summary.put("face_frames", 0);
        summary.put("duration_seconds", BigDecimal.ZERO);
        return summary;
    }

    private Map<String, Object> extractMetrics(Map<String, Object> response) {
        Map<String, Object> metrics = mapValue(response, "metrics", "live_metrics");
        return metrics == null ? new LinkedHashMap<>(response == null ? Map.of() : response) : metrics;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractVideoSummary(Map<String, Object> report) {
        if (report == null) {
            return null;
        }
        // Python /finish 的报告可能仍带 {"report": ...} 外壳，先拆壳再读 video_summary。
        Map<String, Object> source = mapValue(report, "report");
        if (source == null) {
            source = report;
        }
        Map<String, Object> summary = mapValue(source, "video_summary", "videoSummary", "summary");
        return summary == null ? source : summary;
    }

    /** Python 视觉会话保存在内存中，服务重启即失效；按训练记录自动重建并记录事件。 */
    private void recreateAiSession(InterviewRecord record, String reason) {
        String oldSessionId = record.getAiSessionId();
        String newSessionId = aiApiClient.createSession(record.getId(), record.getUserId());
        record.setAiSessionId(newSessionId);
        InterviewRecord update = new InterviewRecord();
        update.setId(record.getId());
        update.setAiSessionId(newSessionId);
        recordMapper.updateById(update);
        log.warn("Python 视觉会话已重建: recordId={}, old={}, new={}, reason={}",
                record.getId(), oldSessionId, newSessionId, reason);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("old_session_id", oldSessionId == null ? "" : oldSessionId);
        payload.put("new_session_id", newSessionId);
        payload.put("reason", reason);
        saveEvent(record, record.getUserId(), "VISION_SESSION_RECREATED", payload);
    }

    /** Python 视觉工作线程异常时输出告警，避免本地模型失败被静默吞掉。 */
    private void logVisionHealth(InterviewRecord record, Map<String, Object> response) {
        Object vision = response == null ? null : response.get("vision");
        if (vision instanceof Map<?, ?> status) {
            if (Boolean.FALSE.equals(status.get("healthy"))) {
                log.warn("Python 视觉分析线程异常: recordId={}, state={}, lastError={}",
                        record.getId(), status.get("state"), status.get("last_error"));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Map<?, ?> map) {
                return new LinkedHashMap<>((Map<String, Object>) map);
            }
        }
        return null;
    }

    private Object first(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal asDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value)).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value))
                || "1".equals(String.valueOf(value));
    }

    private String visionSessionId(InterviewRecord record) {
        return record.getAiSessionId() == null
                ? "record-" + record.getId()
                : record.getAiSessionId();
    }

    private InterviewRecord getRecord(Long recordId) {
        InterviewRecord record = recordId == null ? null : recordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return record;
    }

    private InterviewRecord getRecordBySession(String aiSessionId, Long userId) {
        if (aiSessionId == null || aiSessionId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        InterviewRecord record = recordMapper.selectOne(new LambdaQueryWrapper<InterviewRecord>()
                .eq(InterviewRecord::getAiSessionId, aiSessionId)
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        ensureOperator(record, userId);
        return record;
    }

    private void requireSession(InterviewRecord record) {
        if (record.getAiSessionId() == null || record.getAiSessionId().isBlank()) {
            throw new BusinessException(400, "视觉分析会话尚未创建");
        }
    }

    private void ensureOperator(InterviewRecord record, Long userId) {
        boolean admin = SecurityUtil.hasRole("admin");
        boolean interviewer = SecurityUtil.hasRole("interviewer")
                && (record.getInterviewerId() == null
                || userId.equals(record.getInterviewerId()));
        boolean candidate = record.getUserId().equals(userId);
        if (!admin && !interviewer && !candidate) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void saveEvent(InterviewRecord record, Long userId,
                           String eventType, Map<String, Object> payload) {
        com.iflytek.interview.interview.entity.InterviewSessionEvent event =
                new com.iflytek.interview.interview.entity.InterviewSessionEvent();
        event.setRecordId(record.getId());
        event.setEventType(eventType);
        event.setOperatorId(userId);
        event.setEventTime(LocalDateTime.now());
        try {
            event.setPayloadJson(payload == null ? null : objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("视觉事件载荷序列化失败: recordId={}", record.getId(), e);
        }
        sessionEventMapper.insert(event);
    }
}
