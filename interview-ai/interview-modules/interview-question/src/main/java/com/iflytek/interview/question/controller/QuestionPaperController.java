package com.iflytek.interview.question.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.interview.common.annotation.LogAnnotation;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.question.dto.SmartGroupingRequest;
import com.iflytek.interview.question.entity.Question;
import com.iflytek.interview.question.entity.QuestionPaper;
import com.iflytek.interview.question.entity.QuestionPaperItem;
import com.iflytek.interview.question.mapper.QuestionMapper;
import com.iflytek.interview.question.mapper.QuestionPaperItemMapper;
import com.iflytek.interview.question.mapper.QuestionPaperMapper;
import com.iflytek.interview.question.service.PaperQueryService;
import com.iflytek.interview.question.service.impl.SmartGroupingService;
import com.iflytek.interview.question.vo.PaperDetailVO;
import com.iflytek.interview.question.vo.QuestionVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/question-papers")
public class QuestionPaperController {

    private final SmartGroupingService smartGroupingService;
    private final QuestionMapper questionMapper;
    private final QuestionPaperMapper questionPaperMapper;
    private final QuestionPaperItemMapper questionPaperItemMapper;
    private final PaperQueryService paperQueryService;

    public QuestionPaperController(SmartGroupingService smartGroupingService,
                                   QuestionMapper questionMapper,
                                   QuestionPaperMapper questionPaperMapper,
                                   QuestionPaperItemMapper questionPaperItemMapper,
                                   PaperQueryService paperQueryService) {
        this.smartGroupingService = smartGroupingService;
        this.questionMapper = questionMapper;
        this.questionPaperMapper = questionPaperMapper;
        this.questionPaperItemMapper = questionPaperItemMapper;
        this.paperQueryService = paperQueryService;
    }

    @PreAuthorize("hasAuthority('question:view')")
    @GetMapping
    public Result<List<QuestionPaper>> list(@RequestParam(required = false) Long scenarioId) {
        List<QuestionPaper> papers = questionPaperMapper.selectList(
                new LambdaQueryWrapper<QuestionPaper>()
                        .eq(scenarioId != null, QuestionPaper::getScenarioId, scenarioId)
                        .orderByDesc(QuestionPaper::getCreatedAt));
        return Result.success(papers);
    }

    /** 候选人可用的试卷列表（按场景筛选可用状态的试卷）。 */
    @PreAuthorize("hasRole('candidate') or hasAuthority('question:view')")
    @GetMapping("/available")
    public Result<List<QuestionPaper>> available(@RequestParam Long scenarioId) {
        return Result.success(paperQueryService.listAvailablePapers(scenarioId));
    }

    @PreAuthorize("hasAuthority('question:create')")
    @LogAnnotation(module = "题库管理", operation = "智能组卷")
    @PostMapping("/generate")
    public Result<Long> generate(@RequestBody SmartGroupingRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(smartGroupingService.generatePaper(userId, request));
    }

    @PreAuthorize("hasAuthority('question:view')")
    @GetMapping("/{paperId}")
    public Result<PaperDetailVO> detail(@PathVariable Long paperId) {
        QuestionPaper paper = questionPaperMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        List<Long> questionIds = questionPaperItemMapper.selectList(
                        new LambdaQueryWrapper<QuestionPaperItem>()
                                .eq(QuestionPaperItem::getPaperId, paperId)
                                .orderByAsc(QuestionPaperItem::getSortOrder))
                .stream()
                .map(QuestionPaperItem::getQuestionId)
                .collect(Collectors.toList());

        Map<Long, Question> questionMap = questionIds.isEmpty()
                ? Map.of()
                : questionMapper.selectBatchIds(questionIds).stream()
                        .collect(Collectors.toMap(Question::getId, item -> item));

        List<QuestionVO> questions = questionPaperItemMapper.selectList(
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

        PaperDetailVO vo = new PaperDetailVO();
        vo.setPaper(paper);
        vo.setQuestions(questions);
        return Result.success(vo);
    }
}