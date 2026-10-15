package com.iflytek.interview.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.question.dto.QuestionDTO;
import com.iflytek.interview.question.entity.Question;
import com.iflytek.interview.question.entity.QuestionTag;
import com.iflytek.interview.question.entity.QuestionTagRel;
import com.iflytek.interview.question.mapper.QuestionMapper;
import com.iflytek.interview.question.mapper.QuestionTagMapper;
import com.iflytek.interview.question.mapper.QuestionTagRelMapper;
import com.iflytek.interview.question.service.QuestionCacheService;
import com.iflytek.interview.question.service.QuestionService;
import com.iflytek.interview.question.vo.QuestionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QuestionServiceImpl
        extends ServiceImpl<QuestionMapper, Question>
        implements QuestionService {

    private final QuestionTagMapper questionTagMapper;
    private final QuestionTagRelMapper questionTagRelMapper;
    private final QuestionCacheService questionCacheService;

    public QuestionServiceImpl(QuestionTagMapper questionTagMapper,
                               QuestionTagRelMapper questionTagRelMapper,
                               QuestionCacheService questionCacheService) {
        this.questionTagMapper = questionTagMapper;
        this.questionTagRelMapper = questionTagRelMapper;
        this.questionCacheService = questionCacheService;
    }

    @Override
    @Transactional
    public Long createQuestion(QuestionDTO dto) {
        validate(dto);

        Question question = new Question();
        BeanUtils.copyProperties(dto, question);
        question.setStatus(1);
        if (question.getSortOrder() == null) {
            question.setSortOrder(0);
        }
        save(question);

        bindTags(question.getId(), dto.getTags());
        questionCacheService.clearScenarioCache(dto.getScenarioId());
        log.info("新增题目: id={}, scenarioId={}, tagSize={}",
                question.getId(), question.getScenarioId(),
                dto.getTags() == null ? 0 : dto.getTags().size());
        return question.getId();
    }

    @Override
    @Transactional
    public void updateQuestion(Long id, QuestionDTO dto) {
        validate(dto);

        Question question = getById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }

        BeanUtils.copyProperties(dto, question);
        updateById(question);

        questionTagRelMapper.delete(
                new LambdaQueryWrapper<QuestionTagRel>()
                        .eq(QuestionTagRel::getQuestionId, id));
        bindTags(id, dto.getTags());
        questionCacheService.clearScenarioCache(dto.getScenarioId());
    }

    @Override
    @Transactional
    public void deleteQuestion(Long id) {
        Question question = getById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        removeById(id);
        questionTagRelMapper.delete(
                new LambdaQueryWrapper<QuestionTagRel>()
                        .eq(QuestionTagRel::getQuestionId, id));
        questionCacheService.clearScenarioCache(question.getScenarioId());
    }

    @Override
    public List<Question> randomQuestions(Long scenarioId, int count) {
        int limit = Math.min(Math.max(count, 1), 50);
        return lambdaQuery()
                .eq(Question::getScenarioId, scenarioId)
                .eq(Question::getStatus, 1)
                .last("ORDER BY RAND() LIMIT " + limit)
                .list();
    }

    @Override
    public Page<QuestionVO> pageQuery(long current, long size,
                                      Long scenarioId, Integer difficulty, String keyword) {
        Page<Question> page = new Page<>(current, size);
        Page<Question> result = page(page,
                new LambdaQueryWrapper<Question>()
                        .eq(scenarioId != null, Question::getScenarioId, scenarioId)
                        .eq(difficulty != null, Question::getDifficulty, difficulty)
                        .like(keyword != null && !keyword.isBlank(),
                                Question::getContent, keyword)
                        .orderByDesc(Question::getUpdatedAt));

        List<QuestionVO> records = result.getRecords().stream()
                .map(QuestionVO::from)
                .collect(Collectors.toList());

        Page<QuestionVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public List<String> getTagCodes(Long questionId) {
        List<QuestionTagRel> relations = questionTagRelMapper.selectList(
                new LambdaQueryWrapper<QuestionTagRel>()
                        .eq(QuestionTagRel::getQuestionId, questionId));
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> tagIds = relations.stream()
                .map(QuestionTagRel::getTagId)
                .collect(Collectors.toList());
        Map<Long, String> codeMap = questionTagMapper.selectBatchIds(tagIds)
                .stream()
                .collect(Collectors.toMap(QuestionTag::getId, QuestionTag::getCode, (a, b) -> a));
        return relations.stream()
                .map(item -> codeMap.get(item.getTagId()))
                .filter(code -> code != null)
                .collect(Collectors.toList());
    }

    private void validate(QuestionDTO dto) {
        if (dto.getScenarioId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (dto.getDifficulty() == null || dto.getDifficulty() < 1 || dto.getDifficulty() > 3) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private void bindTags(Long questionId, List<String> tagCodes) {
        if (tagCodes == null || tagCodes.isEmpty()) {
            return;
        }

        List<QuestionTag> tags = questionTagMapper.selectList(
                new LambdaQueryWrapper<QuestionTag>()
                        .in(QuestionTag::getCode, tagCodes)
                        .eq(QuestionTag::getStatus, 1));

        Map<String, QuestionTag> tagMap = tags.stream()
                .collect(Collectors.toMap(QuestionTag::getCode, Function.identity(), (a, b) -> a));

        List<QuestionTagRel> relations = new ArrayList<>();
        for (String code : tagCodes.stream().distinct().collect(Collectors.toList())) {
            QuestionTag tag = tagMap.get(code);
            if (tag == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
            QuestionTagRel rel = new QuestionTagRel();
            rel.setQuestionId(questionId);
            rel.setTagId(tag.getId());
            relations.add(rel);
        }

        for (QuestionTagRel rel : relations) {
            questionTagRelMapper.insert(rel);
        }
    }
}