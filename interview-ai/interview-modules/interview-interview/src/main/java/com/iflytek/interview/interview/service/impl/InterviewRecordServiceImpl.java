package com.iflytek.interview.interview.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.interview.dto.InterviewSessionCreateRequest;
import com.iflytek.interview.interview.dto.InterviewSessionScheduleRequest;
import com.iflytek.interview.interview.dto.RecordScoreDTO;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.entity.InterviewSessionEvent;
import com.iflytek.interview.common.ai.AiApiClient;
import com.iflytek.interview.interview.entity.InterviewStatus;
import com.iflytek.interview.interview.entity.QuestionPhase;
import com.iflytek.interview.interview.entity.Scenario;
import com.iflytek.interview.interview.mapper.InterviewRecordMapper;
import com.iflytek.interview.interview.mapper.InterviewSessionEventMapper;
import com.iflytek.interview.interview.mapper.ScenarioMapper;
import com.iflytek.interview.interview.service.InterviewRecordService;
import com.iflytek.interview.interview.service.InterviewVisionService;
import com.iflytek.interview.interview.service.ReportGenerator;
import com.iflytek.interview.question.service.PaperQueryService;
import com.iflytek.interview.question.vo.QuestionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class InterviewRecordServiceImpl
        extends ServiceImpl<InterviewRecordMapper, InterviewRecord>
        implements InterviewRecordService {

    private static final String SESSION_KEY = "interview:session:%d";
    private static final String SESSION_EVENT_KEY = "interview:session:events:%d";

    @Autowired
    private ReportGenerator reportGenerator;

    @Autowired
    private PaperQueryService paperQueryService;

    @Autowired
    private InterviewSessionEventMapper eventMapper;

    @Autowired
    private ScenarioMapper scenarioMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private AiApiClient aiApiClient;

    @Autowired
    private InterviewVisionService visionService;

    // ===== 基础面试记录 =====

    @Override
    @Transactional
    public InterviewRecord createRecord(Long userId, Long scenarioId, Long paperId) {
        ensureInterviewCandidate();
        validateScenario(scenarioId);
        if (paperId != null) {
            paperQueryService.validatePaper(paperId, scenarioId);
        }
        InterviewRecord record = new InterviewRecord();
        record.setUserId(userId);
        record.setScenarioId(scenarioId);
        record.setPaperId(paperId);
        record.setStatus(InterviewStatus.PENDING.getCode());
        save(record);
        log.info("创建面试: recordId={}, userId={}, scenarioId={}, paperId={}",
                record.getId(), userId, scenarioId, paperId);
        return record;
    }

    @Override
    @Transactional
    public InterviewRecord startInterview(Long recordId, Long userId) {
        InterviewRecord record = checkAndGet(recordId, userId);
        checkTransition(record.getStatus(), InterviewStatus.ONGOING.getCode());
        checkJoinWindow(record);
        record.setStatus(InterviewStatus.ONGOING.getCode());
        record.setStartedAt(LocalDateTime.now());
        record.setQuestionIndex(0);
        ensureAiSession(record);
        updateById(record);
        saveEvent(recordId, userId, "SESSION_STARTED", null);
        clearSessionCache(recordId);
        return record;
    }

    @Override
    @Transactional
    public InterviewRecord submitInterview(Long recordId, Long userId, String answerData) {
        // 行锁串行化完成操作；权限检查仍在返回任何记录之前执行。
        baseMapper.selectForUpdate(recordId);
        InterviewRecord record = checkAndGet(recordId, userId);
        if (InterviewStatus.COMPLETED.getCode().equals(record.getStatus())) {
            return record;
        }
        checkTransition(record.getStatus(), InterviewStatus.COMPLETED.getCode());

        if (answerData != null && answerData.length() > 100_000) {
            throw new BusinessException(400, "答案长度不能超过100000字符");
        }
        String submittedAnswers = answerData == null || answerData.isBlank()
                ? record.getAnswerData() : answerData;

        LocalDateTime now = LocalDateTime.now();
        record.setStatus(InterviewStatus.COMPLETED.getCode());
        record.setAnswerData(submittedAnswers);
        record.setCompletedAt(now);
        if (record.getStartedAt() != null) {
            // interview_record.duration 的单位是秒（见建表注释），前端也按秒换算为“x分y秒”。
            // 此前用 toMinutes() 写入分钟，导致用时展示比实际小 60 倍。
            record.setDuration((int) Duration.between(record.getStartedAt(), now).getSeconds());
        }
        updateById(record);

        saveEvent(recordId, userId, "SESSION_COMPLETED", null);
        // 结束会话取视觉汇总（内部会调用 Python /finish 并落库 video_summary）
        Map<String, Object> videoSummary = visionService.finishAndSave(record.getId(), userId);
        Map<String, Object> analysis = new LinkedHashMap<>(videoSummary);
        // 本地题目送评失败时不能使用另一套题目的会话评分冒充结果。
        analysis.remove(InterviewVisionServiceImpl.AI_REPORT_KEY);
        // 问答评测改走独立接口：把本地题库的真实题面 + 考生作答直接送评，
        // 避免 Python 会话用自己那套题目配对，导致评测与输入不对应。
        Map<String, Object> alignedEvaluation = evaluateWithLocalQuestions(record, submittedAnswers, videoSummary);
        if (alignedEvaluation != null) {
            analysis.put(InterviewVisionServiceImpl.AI_REPORT_KEY, alignedEvaluation);
        }
        reportGenerator.generateReport(record.getId(), analysis);
        releaseAiSession(record);
        clearSessionCache(recordId);
        return getById(recordId);
    }

    /**
     * 用本地题库的真实题面与考生作答调用 Python 独立评测接口。
     * 失败时返回 null，由 ReportGenerator 回退使用会话评测结果。
     */
    private Map<String, Object> evaluateWithLocalQuestions(InterviewRecord record, String answerData,
                                                           Map<String, Object> videoSummary) {
        List<Map<String, String>> qaPairs = toQaPairs(answerData);
        if (qaPairs.isEmpty()) {
            log.info("本次提交没有有效作答，跳过独立 AI 评测: recordId={}", record.getId());
            return null;
        }
        try {
            Map<String, Object> result = aiApiClient.evaluateTranscript(
                    qaPairs, scenarioName(record.getScenarioId()), "中级", videoSummary);
            log.info("已完成与本地题目对齐的 AI 评测: recordId={}, 题目数={}",
                    record.getId(), qaPairs.size());
            return result;
        } catch (Exception e) {
            log.warn("独立 AI 评测失败，将回退使用会话评测结果: recordId={}, err={}",
                    record.getId(), e.getMessage());
            return null;
        }
    }

    /** 把前端提交的作答 JSON 拆成“题面 + 回答”对；非约定结构时整段作为一条回答。 */
    private List<Map<String, String>> toQaPairs(String answerData) {
        List<Map<String, String>> pairs = new ArrayList<>();
        if (answerData == null || answerData.isBlank()) {
            return pairs;
        }
        try {
            JsonNode answers = objectMapper.readTree(answerData).path("answers");
            if (answers.isArray()) {
                for (JsonNode item : answers) {
                    String answer = item.path("answer").asText("").trim();
                    if (answer.isEmpty()) {
                        continue;
                    }
                    Map<String, String> pair = new LinkedHashMap<>();
                    pair.put("question", item.path("content").asText("").trim());
                    pair.put("answer", answer);
                    pairs.add(pair);
                }
                return pairs;
            }
        } catch (Exception e) {
            // 非约定 JSON，按整段文本处理
        }
        Map<String, String> fallback = new LinkedHashMap<>();
        fallback.put("question", "");
        fallback.put("answer", answerData.trim());
        pairs.add(fallback);
        return pairs;
    }

    /** 取面试场景名称作为评测用的目标岗位。 */
    private String scenarioName(Long scenarioId) {
        if (scenarioId == null) {
            return "";
        }
        try {
            Scenario scenario = scenarioMapper.selectById(scenarioId);
            return scenario == null || scenario.getName() == null ? "" : scenario.getName();
        } catch (Exception e) {
            log.warn("读取面试场景失败: scenarioId={}, err={}", scenarioId, e.getMessage());
            return "";
        }
    }

    @Override
    @Transactional
    public void cancelInterview(Long recordId, Long userId) {
        cancelInterview(recordId, userId, null);
    }

    @Override
    @Transactional
    public void cancelInterview(Long recordId, Long userId, String reason) {
        InterviewRecord record = checkAndGet(recordId, userId);
        if (!InterviewStatus.PENDING.getCode().equals(record.getStatus())
                && !InterviewStatus.ONGOING.getCode().equals(record.getStatus())) {
            throw new BusinessException(400, "当前会话状态无法取消");
        }
        String targetStatus = InterviewStatus.CANCELLED.getCode();
        if (!InterviewStatus.canTransition(record.getStatus(), targetStatus)) {
            throw new BusinessException(400, "非法状态转换");
        }
        record.setStatus(targetStatus);
        record.setCompletedAt(LocalDateTime.now());
        updateById(record);
        releaseAiSession(record);

        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason);
        saveEvent(recordId, userId, "SESSION_CANCELLED", toJson(payload));
        clearSessionCache(recordId);
    }

    @Override
    @Transactional
    public InterviewRecord scoreInterview(Long recordId, RecordScoreDTO dto) {
        InterviewRecord record = checkAndGet(recordId, SecurityUtil.getCurrentUserId());
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        if (!InterviewStatus.COMPLETED.getCode().equals(record.getStatus())) {
            throw new BusinessException(400, "仅已完成的面试可以评分");
        }
        record.setScore(dto.getScore());
        record.setAiFeedback(dto.getAiFeedback());
        updateById(record);
        return record;
    }

    @Override
    public List<InterviewRecord> listForCurrentUser() {
        if (SecurityUtil.hasRole("admin") || SecurityUtil.hasRole("interviewer")) {
            return baseMapper.selectAllWithCandidate();
        }
        Long userId = SecurityUtil.getCurrentUserId();
        return lambdaQuery()
                .eq(InterviewRecord::getUserId, userId)
                .orderByDesc(InterviewRecord::getCreatedAt)
                .list();
    }

    @Override
    public List<QuestionVO> getRecordQuestions(Long recordId, Long userId) {
        InterviewRecord record = checkAndGet(recordId, userId);
        // 绑定试卷则返回试卷题目，否则随机取 5 题
        return paperQueryService.getSessionQuestions(
                record.getScenarioId(), record.getPaperId(), 5);
    }

    // ===== 第05讲：面试会话管理 =====

    @Override
    @Transactional
    public InterviewRecord createSession(Long userId, InterviewSessionCreateRequest request) {
        ensureInterviewCandidate();
        validateScenario(request.getScenarioId());
        validateSchedule(request.getScheduledAt(), request.getPlannedEndAt());
        if (request.getScenarioId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (request.getPaperId() != null) {
            paperQueryService.validatePaper(request.getPaperId(), request.getScenarioId());
        }
        if (request.getScheduledAt() != null) {
            checkCandidateConflict(userId, null,
                    request.getScheduledAt(), request.getPlannedEndAt());
        }
        InterviewRecord record = new InterviewRecord();
        record.setUserId(userId);
        record.setScenarioId(request.getScenarioId());
        record.setPaperId(request.getPaperId());

        record.setRoomCode(generateRoomCode());
        record.setScheduledAt(request.getScheduledAt());
        record.setPlannedEndAt(request.getPlannedEndAt());
        record.setReminderSent(0);
        record.setQuestionIndex(0);
        record.setMetadataJson(request.getMetadataJson());
        boolean immediate = request.getScheduledAt() == null;
        record.setStatus(immediate
                ? InterviewStatus.ONGOING.getCode()
                : InterviewStatus.PENDING.getCode());
        if (immediate) {
            record.setStartedAt(LocalDateTime.now());
        }
        save(record);
        if (immediate) {
            ensureAiSession(record);
            updateById(record);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("scenarioId", request.getScenarioId());
        payload.put("paperId", request.getPaperId());
        payload.put("roomCode", record.getRoomCode());
        saveEvent(record.getId(), userId, "SESSION_CREATED", toJson(payload));
        if (immediate) {
            saveEvent(record.getId(), userId, "SESSION_STARTED", null);
        }
        log.info("创建面试会话: recordId={}, userId={}, roomCode={}",
                record.getId(), userId, record.getRoomCode());
        return record;
    }

    @Override
    @Transactional
    public InterviewRecord schedule(Long recordId, Long userId,
                                    InterviewSessionScheduleRequest request) {
        InterviewRecord record = checkAndGet(recordId, userId);
        if (InterviewStatus.COMPLETED.getCode().equals(record.getStatus())
                || InterviewStatus.CANCELLED.getCode().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.RECORD_ALREADY_COMPLETED);
        }
        validateSchedule(request.getScheduledAt(), request.getPlannedEndAt());
        checkCandidateConflict(record.getUserId(), recordId,
                request.getScheduledAt(), request.getPlannedEndAt());

        record.setScheduledAt(request.getScheduledAt());
        record.setPlannedEndAt(request.getPlannedEndAt());
        record.setReminderSent(0);
        updateById(record);

        saveEvent(recordId, userId, "SESSION_SCHEDULED", toJson(request));
        clearSessionCache(recordId);
        return record;
    }

    @Override
    @Transactional
    public InterviewRecord join(Long recordId, Long userId, String role) {
        InterviewRecord record = checkAndGet(recordId, userId);
        String status = record.getStatus();
        if (!InterviewStatus.PENDING.getCode().equals(status)
                && !InterviewStatus.ONGOING.getCode().equals(status)) {
            throw new BusinessException(ErrorCode.RECORD_ALREADY_COMPLETED);
        }
        checkJoinWindow(record);

        if ("HOST".equalsIgnoreCase(role)) {
            if (!SecurityUtil.hasRole("admin")
                    && !SecurityUtil.hasRole("interviewer")) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            if (record.getInterviewerId() == null) {
                record.setInterviewerId(userId);
                updateById(record);
            }
            saveEvent(recordId, userId, "HOST_JOINED", null);
        } else if ("CANDIDATE".equalsIgnoreCase(role)) {
            if (!SecurityUtil.hasRole("admin")
                    && !record.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            saveEvent(recordId, userId, "CANDIDATE_JOINED", null);
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        clearSessionCache(recordId);
        return record;
    }

    @Override
    public InterviewRecord getSession(Long recordId, Long userId) {
        return checkAndGet(recordId, userId);
    }

    @Override
    @Transactional
    public void saveEvent(Long recordId, Long operatorId,
                          String eventType, String payloadJson) {
        InterviewSessionEvent event = new InterviewSessionEvent();
        event.setRecordId(recordId);
        event.setEventType(eventType);
        event.setOperatorId(operatorId);
        event.setEventTime(LocalDateTime.now());
        event.setPayloadJson(payloadJson);
        eventMapper.insert(event);
    }

    /**
     * 开始面试时创建 Python AI 会话，并回填 ai_session_id 与当前阶段。
     * Python 服务暂不可用时仅记录日志，不阻塞面试开始。
     */
    private void ensureAiSession(InterviewRecord record) {
        if (record.getAiSessionId() != null) {
            return;
        }
        try {
            String aiSessionId = aiApiClient.createSession(record.getId(), record.getUserId());
            record.setAiSessionId(aiSessionId);
            record.setCurrentPhase(QuestionPhase.ASKING.getCode());
            log.info("已创建 AI 会话: recordId={}, aiSessionId={}", record.getId(), aiSessionId);
            // 必须先生成开场题，否则 Python 侧 /messages 与 /finish 都会以
            // “请先生成首道面试问题 / 请至少完成一次回答” 拒绝，评分无从产出。
            try {
                aiApiClient.generateOpening(aiSessionId);
                log.info("已生成 AI 开场题: recordId={}, aiSessionId={}", record.getId(), aiSessionId);
            } catch (Exception openingError) {
                log.warn("生成 AI 开场题失败，面试仍可进行: recordId={}, err={}",
                        record.getId(), openingError.getMessage());
            }
        } catch (Exception e) {
            log.warn("创建 AI 会话失败，面试仍可进行: recordId={}, err={}",
                    record.getId(), e.getMessage());
        }
    }

    /**
     * 结束 / 取消面试时清理 Python AI 会话，避免资源泄漏。
     */
    private void releaseAiSession(InterviewRecord record) {
        if (record.getAiSessionId() == null) {
            return;
        }
        try {
            aiApiClient.deleteSession(record.getAiSessionId());
            log.info("已清理 AI 会话: recordId={}, aiSessionId={}",
                    record.getId(), record.getAiSessionId());
        } catch (Exception e) {
            log.warn("清理 AI 会话失败: recordId={}, err={}", record.getId(), e.getMessage());
        }
    }

        // ===== 缓存 =====

    public InterviewRecord getCachedSession(Long recordId) {
        String key = String.format(SESSION_KEY, recordId);
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof InterviewRecord record) {
            return record;
        }
        InterviewRecord record = getById(recordId);
        if (record != null) {
            redisTemplate.opsForValue().set(key, record, java.time.Duration.ofMinutes(10));
        }
        return record;
    }

    private void clearSessionCache(Long recordId) {
        if (recordId == null) {
            return;
        }
        redisTemplate.delete(String.format(SESSION_KEY, recordId));
        redisTemplate.delete(String.format(SESSION_EVENT_KEY, recordId));
    }

    // ===== 工具方法 =====

    private InterviewRecord checkAndGet(Long recordId, Long userId) {
        InterviewRecord record = getById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        boolean admin = SecurityUtil.hasRole("admin");
        boolean interviewer = SecurityUtil.hasRole("interviewer")
                && (record.getInterviewerId() == null
                        || userId.equals(record.getInterviewerId()));
        boolean candidate = userId.equals(record.getUserId());
        if (!admin && !interviewer && !candidate) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return record;
    }

    private void ensureInterviewCandidate() {
        if (SecurityUtil.hasRole("admin")) {
            throw new BusinessException(ErrorCode.ADMIN_CANNOT_INTERVIEW);
        }
        if (!SecurityUtil.hasRole("candidate")) {
            throw new BusinessException(ErrorCode.INTERVIEW_ONLY_CANDIDATE);
        }
    }

    private void validateScenario(Long scenarioId) {
        if (scenarioId == null || scenarioMapper.selectById(scenarioId) == null) {
            throw new BusinessException(400, "面试场景不存在");
        }
    }

    private void checkTransition(String from, String to) {
        if (!InterviewStatus.canTransition(from, to)) {
            throw new BusinessException(400, "非法的状态转换: " + from + " -> " + to);
        }
    }

    private void validateSchedule(LocalDateTime start, LocalDateTime end) {
        if (start == null && end == null) {
            return;
        }
        if (start == null || end == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (!start.isBefore(end)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (start.isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < 15 || minutes > 120) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private void checkCandidateConflict(Long userId, Long excludeRecordId,
                                        LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return;
        }
        List<InterviewRecord> records = lambdaQuery()
                .eq(InterviewRecord::getUserId, userId)
                .ne(excludeRecordId != null, InterviewRecord::getId, excludeRecordId)
                .in(InterviewRecord::getStatus,
                        InterviewStatus.PENDING.getCode(),
                        InterviewStatus.ONGOING.getCode())
                .isNotNull(InterviewRecord::getScheduledAt)
                .isNotNull(InterviewRecord::getPlannedEndAt)
                .list();

        boolean conflict = records.stream().anyMatch(item ->
                item.getScheduledAt().isBefore(end)
                        && item.getPlannedEndAt().isAfter(start));
        if (conflict) {
            throw new BusinessException(400, "预约时间冲突");
        }
    }

    private void checkJoinWindow(InterviewRecord record) {
        LocalDateTime now = LocalDateTime.now();
        if (record.getPlannedEndAt() != null && now.isAfter(record.getPlannedEndAt())) {
            throw new BusinessException(400, "会话已超过计划结束时间");
        }
        if (record.getScheduledAt() != null
                && now.isBefore(record.getScheduledAt().minusMinutes(15))) {
            throw new BusinessException(400, "会话尚未进入可加入时间");
        }
    }

    private String generateRoomCode() {
        return "room-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("会话事件载荷序列化失败", e);
            return null;
        }
    }
}

