package com.iflytek.interview.question.controller;

import com.iflytek.interview.common.annotation.LogAnnotation;
import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.question.dto.QuestionTagDTO;
import com.iflytek.interview.question.entity.QuestionTag;
import com.iflytek.interview.question.service.QuestionTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/question-tags")
public class QuestionTagController {

    @Autowired
    private QuestionTagService questionTagService;

    @PreAuthorize("hasAuthority('question:view')")
    @GetMapping
    public Result<List<QuestionTag>> list(@RequestParam(required = false) String type) {
        return Result.success(questionTagService.listTags(type));
    }

    @PreAuthorize("hasAuthority('question:create')")
    @LogAnnotation(module = "题库管理", operation = "新增标签")
    @PostMapping
    public Result<QuestionTag> create(@RequestBody QuestionTagDTO dto) {
        return Result.success(questionTagService.createTag(dto));
    }

    @PreAuthorize("hasAuthority('question:update')")
    @LogAnnotation(module = "题库管理", operation = "修改标签")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody QuestionTagDTO dto) {
        questionTagService.updateTag(id, dto);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('question:update')")
    @LogAnnotation(module = "题库管理", operation = "停用标签")
    @PatchMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        questionTagService.disableTag(id);
        return Result.success();
    }
}