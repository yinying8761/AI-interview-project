package com.iflytek.interview.question.service;

import com.iflytek.interview.question.entity.Question;
import com.iflytek.interview.question.mapper.QuestionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 题库缓存：缓存场景题目 ID 集合，用于降低热点场景的数据库查询压力。
 * 使用 StringRedisTemplate 统一以字符串序列化，避免与 RedisTemplate 的 JSON 序列化口径冲突。
 */
@Slf4j
@Component
public class QuestionCacheService {

    private static final String SCENARIO_QUESTION_KEY = "interview:question:ids:%d";
    private static final String TAG_QUESTION_KEY = "interview:question:tag:%d";
    private static final String PAPER_KEY = "interview:question:paper:%d";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final QuestionMapper questionMapper;

    public QuestionCacheService(StringRedisTemplate redisTemplate, QuestionMapper questionMapper) {
        this.redisTemplate = redisTemplate;
        this.questionMapper = questionMapper;
    }

    /** 读取场景题目 ID 列表；未命中时从数据库加载并写缓存。 */
    public List<Long> getCachedQuestionIds(Long scenarioId) {
        String key = String.format(SCENARIO_QUESTION_KEY, scenarioId);
        List<String> values = redisTemplate.opsForList().range(key, 0, -1);
        if (values != null && !values.isEmpty()) {
            return values.stream().map(Long::valueOf).collect(Collectors.toList());
        }

        List<Long> ids = questionMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                                .eq(Question::getScenarioId, scenarioId)
                                .eq(Question::getStatus, 1)
                                .select(Question::getId))
                .stream()
                .map(Question::getId)
                .collect(Collectors.toList());

        if (!ids.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(
                    key, ids.stream().map(String::valueOf).collect(Collectors.toList()));
            redisTemplate.expire(key, CACHE_TTL);
        }
        return ids;
    }

    /** 题目新增、修改、删除、导入成功后清理对应场景缓存。 */
    public void clearScenarioCache(Long scenarioId) {
        if (scenarioId == null) {
            return;
        }
        String key = String.format(SCENARIO_QUESTION_KEY, scenarioId);
        redisTemplate.delete(key);
        log.info("清理题目场景缓存: key={}", key);
    }

    /** 标签启用/禁用或绑定变化后清理标签题目缓存。 */
    public void clearTagCache(Long tagId) {
        if (tagId == null) {
            return;
        }
        String key = String.format(TAG_QUESTION_KEY, tagId);
        redisTemplate.delete(key);
        log.info("清理题目标签缓存: key={}", key);
    }

    /** 试卷更新或删除后清理试卷缓存。 */
    public void clearPaperCache(Long paperId) {
        if (paperId == null) {
            return;
        }
        String key = String.format(PAPER_KEY, paperId);
        redisTemplate.delete(key);
        log.info("清理试卷缓存: key={}", key);
    }

    /** 缓存试卷的题目 ID 列表。 */
    public void cachePaperQuestions(Long paperId, List<Long> questionIds) {
        if (paperId == null || questionIds == null || questionIds.isEmpty()) {
            return;
        }
        String key = String.format(PAPER_KEY, paperId);
        redisTemplate.opsForList().rightPushAll(
                key, questionIds.stream().map(String::valueOf).collect(Collectors.toList()));
        redisTemplate.expire(key, CACHE_TTL);
    }

    /** 读取缓存中的试卷题目 ID 列表，未命中返回空集合。 */
    public List<Long> getCachedPaperQuestionIds(Long paperId) {
        String key = String.format(PAPER_KEY, paperId);
        List<String> values = redisTemplate.opsForList().range(key, 0, -1);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream().map(Long::valueOf).collect(Collectors.toList());
    }
}