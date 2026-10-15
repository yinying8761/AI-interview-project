package com.iflytek.interview.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.question.dto.SmartGroupingRequest;
import com.iflytek.interview.question.entity.Question;
import com.iflytek.interview.question.entity.QuestionPaper;
import com.iflytek.interview.question.entity.QuestionPaperItem;
import com.iflytek.interview.question.entity.QuestionStat;
import com.iflytek.interview.question.entity.QuestionTag;
import com.iflytek.interview.question.entity.QuestionTagRel;
import com.iflytek.interview.question.mapper.QuestionMapper;
import com.iflytek.interview.question.mapper.QuestionPaperItemMapper;
import com.iflytek.interview.question.mapper.QuestionPaperMapper;
import com.iflytek.interview.question.mapper.QuestionStatMapper;
import com.iflytek.interview.question.mapper.QuestionTagMapper;
import com.iflytek.interview.question.mapper.QuestionTagRelMapper;
import com.iflytek.interview.question.service.QuestionCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 智能组卷：在候选题目池中按难度计划、知识/岗位/轮次标签、使用频率进行打分与抽题，
 * 并对结果去重、兜底补齐后持久化为试卷。
 */
@Slf4j
@Service
public class SmartGroupingService {

    private static final int KNOWLEDGE_WEIGHT = 2;
    private static final int JOB_WEIGHT = 3;
    private static final int ROUND_WEIGHT = 2;

    private final QuestionMapper questionMapper;
    private final QuestionTagMapper questionTagMapper;
    private final QuestionTagRelMapper questionTagRelMapper;
    private final QuestionPaperMapper questionPaperMapper;
    private final QuestionPaperItemMapper questionPaperItemMapper;
    private final QuestionStatMapper questionStatMapper;
    private final QuestionCacheService questionCacheService;
    private final ObjectMapper objectMapper;

    public SmartGroupingService(QuestionMapper questionMapper,
                                QuestionTagMapper questionTagMapper,
                                QuestionTagRelMapper questionTagRelMapper,
                                QuestionPaperMapper questionPaperMapper,
                                QuestionPaperItemMapper questionPaperItemMapper,
                                QuestionStatMapper questionStatMapper,
                                QuestionCacheService questionCacheService,
                                ObjectMapper objectMapper) {
        this.questionMapper = questionMapper;
        this.questionTagMapper = questionTagMapper;
        this.questionTagRelMapper = questionTagRelMapper;
        this.questionPaperMapper = questionPaperMapper;
        this.questionPaperItemMapper = questionPaperItemMapper;
        this.questionStatMapper = questionStatMapper;
        this.questionCacheService = questionCacheService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Long generatePaper(Long userId, SmartGroupingRequest request) {
        validate(request);

        List<Question> candidates = loadCandidates(request);
        if (candidates.size() < request.getTotalCount()) {
            throw new BusinessException(ErrorCode.QUESTION_POOL_INSUFFICIENT);
        }

        Map<Long, Set<Long>> tagMap = loadTagMap(candidates);
        Map<Long, Integer> useCountMap = loadUseCounts(candidates);

        Set<Long> knowledgeTagIds = resolveTagIds(request.getKnowledgeTags());
        Set<Long> jobTagIds = resolveTagIds(request.getJobTags());
        Set<Long> roundTagIds = resolveTagIds(request.getRoundTags());

        Map<Integer, List<Question>> pools = candidates.stream()
                .collect(Collectors.groupingBy(Question::getDifficulty));

        // 知识标签仅作为优选依据：若命中数量足够则优先从命中池抽取，
        // 不足时回退到完整场景候选池，避免因标签覆盖不足而整体组卷失败。
        if (!knowledgeTagIds.isEmpty()) {
            Map<Integer, List<Question>> filtered = filtersByKnowledge(pools, tagMap, knowledgeTagIds);
            int filteredSize = filtered.values().stream().mapToInt(List::size).sum();
            if (filteredSize > 0) {
                pools = filtered;
                log.info("知识标签命中候选 {} 道，优先抽取", filteredSize);
            }
        }

        Map<Integer, Integer> difficultyPlan = buildDifficultyPlan(
                request.getTotalCount(), request.getDifficultyPlan());

        LinkedHashSet<Long> selected = new LinkedHashSet<>();

        for (Map.Entry<Integer, Integer> entry : difficultyPlan.entrySet()) {
            takeQuestions(pools, entry.getKey(), entry.getValue(),
                    selected, tagMap, useCountMap, knowledgeTagIds, jobTagIds, roundTagIds);
        }

        // 分层抽取不足时，从剩余候选题目兜底补齐（优先命中标签的题目，同分保持随机）
        if (selected.size() < request.getTotalCount()) {
            List<Question> remaining = candidates.stream()
                    .filter(item -> !selected.contains(item.getId()))
                    .collect(Collectors.toList());
            Collections.shuffle(remaining);
            remaining.sort(Comparator.comparingInt((Question item) ->
                    -score(item, tagMap, useCountMap, knowledgeTagIds, jobTagIds, roundTagIds)));
            for (Question item : remaining) {
                if (selected.size() >= request.getTotalCount()) {
                    break;
                }
                selected.add(item.getId());
            }
        }

        if (selected.size() < request.getTotalCount()) {
            throw new BusinessException(ErrorCode.QUESTION_POOL_INSUFFICIENT);
        }

        return savePaper(userId, request, selected, tagMap, useCountMap,
                knowledgeTagIds, jobTagIds, roundTagIds);
    }

    private void validate(SmartGroupingRequest request) {
        if (request.getScenarioId() == null || request.getTotalCount() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (request.getTotalCount() < 1 || request.getTotalCount() > 50) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (request.getDifficultyPlan() != null) {
            int sum = request.getDifficultyPlan().values().stream()
                    .mapToInt(Integer::intValue).sum();
            if (sum != request.getTotalCount()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
        }
    }

    private List<Question> loadCandidates(SmartGroupingRequest request) {
        return questionMapper.selectList(
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getScenarioId, request.getScenarioId())
                        .eq(Question::getStatus, 1));
    }

    private Map<Long, Set<Long>> loadTagMap(List<Question> candidates) {
        if (candidates.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> questionIds = candidates.stream()
                .map(Question::getId)
                .collect(Collectors.toList());
        List<QuestionTagRel> rels = questionTagRelMapper.selectList(
                new LambdaQueryWrapper<QuestionTagRel>()
                        .in(QuestionTagRel::getQuestionId, questionIds));
        return rels.stream().collect(Collectors.groupingBy(
                QuestionTagRel::getQuestionId,
                Collectors.mapping(QuestionTagRel::getTagId, Collectors.toSet())));
    }

    private Map<Long, Integer> loadUseCounts(List<Question> candidates) {
        if (candidates.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> questionIds = candidates.stream()
                .map(Question::getId)
                .collect(Collectors.toList());
        return questionStatMapper.selectList(
                        new LambdaQueryWrapper<QuestionStat>()
                                .in(QuestionStat::getQuestionId, questionIds))
                .stream()
                .collect(Collectors.toMap(QuestionStat::getQuestionId,
                        QuestionStat::getUseCount, (a, b) -> a));
    }

    private Set<Long> resolveTagIds(List<String> tagCodes) {
        if (tagCodes == null || tagCodes.isEmpty()) {
            return Collections.emptySet();
        }
        List<QuestionTag> tags = questionTagMapper.selectList(
                new LambdaQueryWrapper<QuestionTag>()
                        .in(QuestionTag::getCode, tagCodes)
                        .eq(QuestionTag::getStatus, 1));
        return tags.stream().map(QuestionTag::getId).collect(Collectors.toSet());
    }

    private Map<Integer, List<Question>> filtersByKnowledge(
            Map<Integer, List<Question>> pools,
            Map<Long, Set<Long>> tagMap,
            Set<Long> knowledgeTagIds) {
        Map<Integer, List<Question>> filtered = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Question>> entry : pools.entrySet()) {
            List<Question> kept = entry.getValue().stream()
                    .filter(item -> {
                        Set<Long> tags = tagMap.getOrDefault(item.getId(), Collections.emptySet());
                        return tags.stream().anyMatch(knowledgeTagIds::contains);
                    })
                    .collect(Collectors.toList());
            if (!kept.isEmpty()) {
                filtered.put(entry.getKey(), kept);
            }
        }
        return filtered;
    }

    private Map<Integer, Integer> buildDifficultyPlan(
            int total, Map<Integer, Integer> customPlan) {
        if (customPlan != null && !customPlan.isEmpty()) {
            return new LinkedHashMap<>(customPlan);
        }
        int easy = (int) Math.round(total * 0.3);
        int hard = (int) Math.round(total * 0.2);
        int medium = total - easy - hard;
        Map<Integer, Integer> plan = new LinkedHashMap<>();
        plan.put(1, easy);
        plan.put(2, medium);
        plan.put(3, hard);
        return plan;
    }

    private void takeQuestions(Map<Integer, List<Question>> pools,
                               Integer difficulty,
                               Integer count,
                               Set<Long> selected,
                               Map<Long, Set<Long>> tagMap,
                               Map<Long, Integer> useCountMap,
                               Set<Long> knowledgeTagIds,
                               Set<Long> jobTagIds,
                               Set<Long> roundTagIds) {
        List<Question> pool = pools.getOrDefault(difficulty, new ArrayList<>());
        pool.removeIf(item -> selected.contains(item.getId()));

        Collections.shuffle(pool);
        pool.sort(Comparator.comparingInt((Question item) ->
                        -score(item, tagMap, useCountMap, knowledgeTagIds, jobTagIds, roundTagIds))
                .thenComparing(Question::getId));

        int limit = Math.min(count, pool.size());
        for (int i = 0; i < limit; i++) {
            selected.add(pool.get(i).getId());
        }
    }

    private int score(Question item,
                      Map<Long, Set<Long>> tagMap,
                      Map<Long, Integer> useCountMap,
                      Set<Long> knowledgeTagIds,
                      Set<Long> jobTagIds,
                      Set<Long> roundTagIds) {
        Set<Long> tags = tagMap.getOrDefault(item.getId(), Collections.emptySet());
        long knowledgeHit = tags.stream().filter(knowledgeTagIds::contains).count();
        long jobHit = tags.stream().filter(jobTagIds::contains).count();
        long roundHit = tags.stream().filter(roundTagIds::contains).count();
        int penalty = Math.min(useCountMap.getOrDefault(item.getId(), 0), 3);
        return (int) (knowledgeHit * KNOWLEDGE_WEIGHT
                + jobHit * JOB_WEIGHT
                + roundHit * ROUND_WEIGHT) - penalty;
    }

    private Long savePaper(Long userId,
                           SmartGroupingRequest request,
                           Set<Long> selected,
                           Map<Long, Set<Long>> tagMap,
                           Map<Long, Integer> useCountMap,
                           Set<Long> knowledgeTagIds,
                           Set<Long> jobTagIds,
                           Set<Long> roundTagIds) {
        QuestionPaper paper = new QuestionPaper();
        paper.setUserId(userId);
        paper.setScenarioId(request.getScenarioId());
        paper.setTitle(request.getTitle() == null || request.getTitle().isBlank()
                ? "智能组卷-" + System.currentTimeMillis()
                : request.getTitle());
        paper.setRound(resolveRoundCode(request));
        paper.setTotalCount(request.getTotalCount());
        paper.setStatus("GENERATED");

        try {
            paper.setStrategyJson(objectMapper.writeValueAsString(request));
        } catch (Exception e) {
            log.error("序列化组卷策略失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        questionPaperMapper.insert(paper);

        int sortOrder = 1;
        for (Long questionId : selected) {
            QuestionPaperItem item = new QuestionPaperItem();
            item.setPaperId(paper.getId());
            item.setQuestionId(questionId);
            item.setSortOrder(sortOrder++);
            item.setScore(10);
            questionPaperItemMapper.insert(item);
        }

        recordUsage(selected);
        questionCacheService.cachePaperQuestions(paper.getId(), new ArrayList<>(selected));
        log.info("生成试卷: paperId={}, questionCount={}", paper.getId(), selected.size());
        return paper.getId();
    }

    /** 根据轮次标签或轮次数映射轮次编码。 */
    private String resolveRoundCode(SmartGroupingRequest request) {
        if (request.getRoundTags() != null && !request.getRoundTags().isEmpty()) {
            return request.getRoundTags().get(0);
        }
        Integer round = request.getRound();
        if (round == null) {
            return "TECH_ROUND_1";
        }
        return switch (round) {
            case 2 -> "TECH_ROUND_2";
            case 3 -> "HR_ROUND";
            default -> "TECH_ROUND_1";
        };
    }

    /** 组卷完成后累加每题使用次数，用于后续降低重复出题概率。 */
    private void recordUsage(Set<Long> questionIds) {
        LocalDateTime now = LocalDateTime.now();
        for (Long questionId : questionIds) {
            QuestionStat stat = questionStatMapper.selectOne(
                    new LambdaQueryWrapper<QuestionStat>()
                            .eq(QuestionStat::getQuestionId, questionId));
            if (stat == null) {
                stat = new QuestionStat();
                stat.setQuestionId(questionId);
                stat.setUseCount(1);
                stat.setLastUsedAt(now);
                questionStatMapper.insert(stat);
            } else {
                stat.setUseCount((stat.getUseCount() == null ? 0 : stat.getUseCount()) + 1);
                stat.setLastUsedAt(now);
                questionStatMapper.updateById(stat);
            }
        }
    }
}