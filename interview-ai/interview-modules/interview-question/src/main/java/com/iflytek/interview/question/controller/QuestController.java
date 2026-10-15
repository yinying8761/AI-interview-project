package com.iflytek.interview.question.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.interview.common.annotation.LogAnnotation;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.question.dto.QuestionDTO;
import com.iflytek.interview.question.entity.Question;
import com.iflytek.interview.question.service.QuestionImportService;
import com.iflytek.interview.question.service.QuestionService;
import com.iflytek.interview.question.vo.ImportResultVO;
import com.iflytek.interview.question.vo.QuestionVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/questions")
public class QuestController {

    @Autowired
    private QuestionService questionService;

    @Autowired
    private QuestionImportService questionImportService;

    @PreAuthorize("hasAuthority('question:view') and !hasRole('candidate')")
    @GetMapping
    public Result<List<QuestionVO>> list(
            @RequestParam(required = false) Long scenarioId,
            @RequestParam(required = false) Integer difficulty) {
        List<Question> list = questionService.lambdaQuery()
                .eq(scenarioId != null, Question::getScenarioId, scenarioId)
                .eq(difficulty != null, Question::getDifficulty, difficulty)
                .eq(Question::getStatus, 1)
                .orderByAsc(Question::getSortOrder)
                .list();
        return Result.success(list.stream().map(this::toVO).collect(Collectors.toList()));
    }

    @PreAuthorize("hasAuthority('question:view') and !hasRole('candidate')")
    @GetMapping("/page")
    public Result<Page<QuestionVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long scenarioId,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String keyword) {
        return Result.success(questionService.pageQuery(current, size, scenarioId, difficulty, keyword));
    }

    @PreAuthorize("hasAuthority('question:view') and !hasRole('candidate')")
    @GetMapping("/{id}")
    public Result<QuestionVO> get(@PathVariable Long id) {
        Question question = questionService.getById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        return Result.success(toVO(question));
    }

    @PreAuthorize("hasAuthority('question:view') and !hasRole('candidate')")
    @GetMapping("/{id}/tags")
    public Result<List<String>> tags(@PathVariable Long id) {
        Question question = questionService.getById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        return Result.success(questionService.getTagCodes(id));
    }

    @PreAuthorize("hasRole('candidate') or hasAuthority('question:view')")
    @GetMapping("/random")
    public Result<List<QuestionVO>> random(
            @RequestParam Long scenarioId,
            @RequestParam(defaultValue = "5") int count) {
        return Result.success(questionService.randomQuestions(scenarioId, count)
                .stream()
                .map(QuestionVO::from)
                .collect(Collectors.toList()));
    }

    @PreAuthorize("hasAuthority('question:create')")
    @LogAnnotation(module = "题库管理", operation = "新增题目")
    @PostMapping
    public Result<Void> create(@RequestBody QuestionDTO dto) {
        questionService.createQuestion(dto);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('question:update')")
    @LogAnnotation(module = "题库管理", operation = "修改题目")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody QuestionDTO dto) {
        questionService.updateQuestion(id, dto);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('question:delete')")
    @LogAnnotation(module = "题库管理", operation = "删除题目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        questionService.deleteQuestion(id);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('question:create')")
    @LogAnnotation(module = "题库管理", operation = "批量导入题目")
    @PostMapping("/import")
    public Result<ImportResultVO> importQuestions(@RequestParam("file") MultipartFile file) {
        return Result.success(questionImportService.importQuestions(file));
    }

    @PreAuthorize("hasAuthority('question:view')")
    @GetMapping("/export")
    public void export(@RequestParam(required = false) Long scenarioId,
                       HttpServletResponse response) {
        questionImportService.exportQuestions(response, scenarioId);
    }

    /**
     * 管理员和面试官返回完整题库字段，候选人仍使用脱敏 VO，避免提前看到参考答案。
     */
    private QuestionVO toVO(Question question) {
        return SecurityUtil.hasRole("admin") || SecurityUtil.hasRole("interviewer")
                ? QuestionVO.fromForManagement(question)
                : QuestionVO.from(question);
    }
}