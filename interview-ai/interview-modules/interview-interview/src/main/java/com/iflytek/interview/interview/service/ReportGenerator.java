package com.iflytek.interview.interview.service;

import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.mapper.InterviewRecordMapper;
import com.iflytek.interview.interview.service.impl.InterviewVisionServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 异步评测报告生成器。
 *
 * <p>评分口径与 Python 侧 score_fusion 完全一致：
 * <pre>
 *   视频可用   ：综合分 = 问答分 × 0.8 + (表情分 × 0.5 + 注意力分 × 0.5) × 0.2
 *   视频不可用 ：综合分 = 问答分（不重新分配权重，避免制造虚高成绩）
 * </pre>
 *
 * <p>此前本类使用写死的 BASELINE_SCORE=82.50 与 85/15 权重，导致所有面试恒得 82.50；
 * 而 Python /finish 早已返回 question_evaluation（逐题评估）与 score_fusion（问答分与
 * 融合公式），只是被调用链丢弃。现改为真实取用这两个字段。
 */
@Slf4j
@Component
public class ReportGenerator {

    /** 问答维度权重。 */
    private static final BigDecimal QUESTION_WEIGHT = new BigDecimal("0.80");
    /** 视频（表达 + 注意力）维度权重。 */
    private static final BigDecimal VIDEO_WEIGHT = new BigDecimal("0.20");
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final int SCALE = 2;

    @Autowired
    private InterviewRecordMapper recordMapper;

    public void generateReport(Long recordId) {
        generateReport(recordId, Map.of("video_available", false));
    }

    @Async("reportExecutor")
    public void generateReport(Long recordId, Map<String, Object> analysisResult) {
        InterviewRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            log.warn("[异步] 找不到面试记录 recordId={}", recordId);
            return;
        }

        Map<String, Object> safeResult = analysisResult == null ? Map.of() : analysisResult;
        Map<String, Object> aiReport = asMap(safeResult.get(InterviewVisionServiceImpl.AI_REPORT_KEY));
        Map<String, Object> questionEvaluation = asMap(aiReport.get("question_evaluation"));
        Map<String, Object> scoreFusion = asMap(aiReport.get("score_fusion"));

        // 问答分优先取 score_fusion.question_score，缺失时回退逐题评估的 overall_score
        BigDecimal questionScore = asDecimal(scoreFusion.get("question_score"));
        if (questionScore == null) {
            questionScore = asDecimal(questionEvaluation.get("overall_score"));
        }

        BigDecimal expressionScore = asDecimal(safeResult.get("expression_score"));
        BigDecimal attentionScore = asDecimal(safeResult.get("attention_score"));
        boolean videoAvailable = asBoolean(safeResult, "video_available", "available");

        BigDecimal score;
        String feedback;
        if (questionScore == null) {
            // 拿不到问答分就如实留空，不用占位分值伪造成绩
            score = null;
            feedback = "本次未取得问答评测得分（大模型评测不可用或未作答），因此不生成综合评分。"
                    + "视频维度：" + (videoAvailable ? "已采集到有效样本。" : "有效样本不足。");
        } else if (videoAvailable && expressionScore != null && attentionScore != null) {
            BigDecimal visualScore = expressionScore.add(attentionScore)
                    .divide(TWO, SCALE, RoundingMode.HALF_UP);
            score = questionScore.multiply(QUESTION_WEIGHT)
                    .add(visualScore.multiply(VIDEO_WEIGHT))
                    .setScale(SCALE, RoundingMode.HALF_UP);
            feedback = buildFeedback(questionEvaluation, score, questionScore, visualScore);
        } else {
            // 有效视频样本不足：直接采用问答分，权重不重分配
            score = questionScore.setScale(SCALE, RoundingMode.HALF_UP);
            feedback = buildFeedback(questionEvaluation, score, questionScore, null);
        }

        InterviewRecord update = new InterviewRecord();
        update.setId(recordId);
        update.setScore(score);
        update.setAiFeedback(feedback);
        recordMapper.updateById(update);

        log.info("[异步] 评测报告生成完成 recordId={}, questionScore={}, videoAvailable={}, finalScore={}",
                recordId, questionScore, videoAvailable, score);
    }

    /** 把大模型返回的逐题评估拼成可直接展示的分段评语。 */
    private String buildFeedback(Map<String, Object> evaluation, BigDecimal finalScore,
                                 BigDecimal questionScore, BigDecimal visualScore) {
        if (evaluation == null || evaluation.isEmpty()) {
            return "综合评分 " + finalScore + " 分（问答 " + questionScore + " 分）。"
                    + (visualScore == null
                    ? "有效视频样本不足，视频维度未计入。"
                    : "视频维度 " + visualScore + " 分。");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("综合评分 ").append(finalScore).append(" 分");
        String level = asString(evaluation.get("level"));
        if (level != null && !level.isBlank()) {
            sb.append("（").append(level).append("）");
        }
        sb.append("；问答 ").append(questionScore).append(" 分");
        sb.append(visualScore == null
                ? "，有效视频样本不足，权重未重新分配。"
                : "，视频 " + visualScore + " 分，按问答 80% / 视频 20% 融合。");

        String summary = asString(evaluation.get("summary"));
        if (summary != null && !summary.isBlank()) {
            sb.append("\n\n【总体评价】\n").append(summary);
        }

        appendDimensions(sb, asMap(evaluation.get("dimensions")));
        appendList(sb, "优势", evaluation.get("strengths"));
        appendList(sb, "待改进", evaluation.get("improvements"));
        appendList(sb, "提升建议", evaluation.get("next_steps"));
        appendReviews(sb, evaluation.get("question_reviews"));

        return sb.toString().trim();
    }

    private void appendDimensions(StringBuilder sb, Map<String, Object> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return;
        }
        sb.append("\n\n【能力维度】");
        dimensions.forEach((name, value) -> sb.append("\n· ").append(name).append("：").append(value));
    }

    private void appendList(StringBuilder sb, String title, Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        sb.append("\n\n【").append(title).append("】");
        int index = 1;
        for (Object item : list) {
            String text = asString(item);
            if (text != null && !text.isBlank()) {
                sb.append("\n").append(index++).append(". ").append(text);
            }
        }
    }

    private void appendReviews(StringBuilder sb, Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        sb.append("\n\n【逐题点评】");
        int index = 1;
        for (Object item : list) {
            Map<String, Object> review = asMap(item);
            if (review.isEmpty()) {
                continue;
            }
            sb.append("\n\n").append(index++).append(". 提问：").append(asString(review.get("question")));
            String assessment = asString(review.get("assessment"));
            if (assessment != null && !assessment.isBlank()) {
                sb.append("\n   评价：").append(assessment);
            }
            String suggestion = asString(review.get("suggestion"));
            if (suggestion != null && !suggestion.isBlank()) {
                sb.append("\n   建议：").append(suggestion);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
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

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean asBoolean(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return false;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                return Boolean.parseBoolean(String.valueOf(value))
                        || "1".equals(String.valueOf(value));
            }
        }
        return false;
    }
}
