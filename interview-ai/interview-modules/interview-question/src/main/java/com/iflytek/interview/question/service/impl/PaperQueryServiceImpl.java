package com.iflytek.interview.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.question.entity.Question;
import com.iflytek.interview.question.entity.QuestionPaper;
import com.iflytek.interview.question.entity.QuestionPaperItem;
import com.iflytek.interview.question.mapper.QuestionMapper;
import com.iflytek.interview.question.mapper.QuestionPaperItemMapper;
import com.iflytek.interview.question.mapper.QuestionPaperMapper;
import com.iflytek.interview.question.service.PaperQueryService;
import com.iflytek.interview.question.service.QuestionService;
import com.iflytek.interview.question.vo.QuestionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaperQueryServiceImpl implements PaperQueryService {

    private final QuestionPaperMapper questionPaperMapper;
    private final QuestionPaperItemMapper questionPaperItemMapper;
    private final QuestionMapper questionMapper;
    private final QuestionService questionService;

    public PaperQueryServiceImpl(QuestionPaperMapper questionPaperMapper,
                                 QuestionPaperItemMapper questionPaperItemMapper,
                                 QuestionMapper questionMapper,
                                 QuestionService questionService) {
        this.questionPaperMapper = questionPaperMapper;
        this.questionPaperItemMapper = questionPaperItemMapper;
        this.questionMapper = questionMapper;
        this.questionService = questionService;
    }

    @Override
    public QuestionPaper getPaper(Long paperId) {
        QuestionPaper paper = questionPaperMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return paper;
    }

    @Override
    public List<QuestionPaper> listAvailablePapers(Long scenarioId) {
        return questionPaperMapper.selectList(
                new LambdaQueryWrapper<QuestionPaper>()
                        .eq(QuestionPaper::getScenarioId, scenarioId)
                        .in(QuestionPaper::getStatus, "GENERATED", "ENABLED")
                        .orderByDesc(QuestionPaper::getCreatedAt));
    }

    @Override
    public List<QuestionVO> getPaperQuestions(Long paperId) {
        List<Long> questionIds = questionPaperItemMapper.selectList(
                        new LambdaQueryWrapper<QuestionPaperItem>()
                                .eq(QuestionPaperItem::getPaperId, paperId)
                                .orderByAsc(QuestionPaperItem::getSortOrder))
                .stream()
                .map(QuestionPaperItem::getQuestionId)
                .collect(Collectors.toList());
        if (questionIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Question> questionMap = questionMapper.selectBatchIds(questionIds)
                .stream()
                .collect(Collectors.toMap(Question::getId, item -> item));

        return questionPaperItemMapper.selectList(
                        new LambdaQueryWrapper<QuestionPaperItem>()
                                .eq(QuestionPaperItem::getPaperId, paperId)
                                .orderByAsc(QuestionPaperItem::getSortOrder))
                .stream()
                .map(item -> {
                    Question question = questionMap.get(item.getQuestionId());
                    if (question == null) {
                        return null;
                    }
                    QuestionVO vo = QuestionVO.from(question);
                    vo.setSortOrder(item.getSortOrder());
                    return vo;
                })
                .filter(q -> q != null)
                .collect(Collectors.toList());
    }

    @Override
    public List<QuestionVO> getSessionQuestions(Long scenarioId, Long paperId, Integer count) {
        if (paperId != null) {
            List<QuestionVO> questions = getPaperQuestions(paperId);
            log.info("面试会话取题: paperId={}, count={}", paperId, questions.size());
            return questions;
        }
        int n = count == null ? 5 : count;
        return questionService.randomQuestions(scenarioId, n)
                .stream()
                .map(QuestionVO::from)
                .collect(Collectors.toList());
    }

    @Override
    public void validatePaper(Long paperId, Long scenarioId) {
        QuestionPaper paper = getPaper(paperId);
        if (!paper.getScenarioId().equals(scenarioId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (!"GENERATED".equals(paper.getStatus()) && !"ENABLED".equals(paper.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }
}