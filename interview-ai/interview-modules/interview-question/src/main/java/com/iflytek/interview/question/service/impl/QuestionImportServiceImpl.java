package com.iflytek.interview.question.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.question.dto.QuestionDTO;
import com.iflytek.interview.question.dto.QuestionImportRow;
import com.iflytek.interview.question.entity.Question;
import com.iflytek.interview.question.mapper.QuestionMapper;
import com.iflytek.interview.question.service.QuestionCacheService;
import com.iflytek.interview.question.service.QuestionImportService;
import com.iflytek.interview.question.service.QuestionService;
import com.iflytek.interview.question.vo.ImportResultVO;
import com.iflytek.interview.question.vo.QuestionExportRow;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题库批量导入导出：使用 EasyExcel，按行校验并记录错误行。
 */
@Slf4j
@Service
public class QuestionImportServiceImpl implements QuestionImportService {

    private static final Set<String> ALLOWED_TYPES = Set.of("technical", "behavioral", "hr");

    private final QuestionService questionService;
    private final QuestionMapper questionMapper;
    private final QuestionCacheService questionCacheService;

    public QuestionImportServiceImpl(QuestionService questionService,
                                     QuestionMapper questionMapper,
                                     QuestionCacheService questionCacheService) {
        this.questionService = questionService;
        this.questionMapper = questionMapper;
        this.questionCacheService = questionCacheService;
    }

    @Override
    @Transactional
    public ImportResultVO importQuestions(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = filename.toLowerCase();
        if (!(lower.endsWith(".xlsx") || lower.endsWith(".xls"))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        List<QuestionImportRow> rows;
        try {
            rows = EasyExcel.read(file.getInputStream())
                    .head(QuestionImportRow.class)
                    .sheet()
                    .doReadSync();
        } catch (IOException e) {
            log.error("读取导入文件失败", e);
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        ImportResultVO result = new ImportResultVO();
        result.setTotal(rows.size());
        Set<Long> affectedScenarios = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2;
            QuestionImportRow row = rows.get(i);
            String reason = validateRow(row);
            if (reason != null) {
                result.setFailCount(result.getFailCount() + 1);
                result.getErrors().add(new ImportResultVO.ErrorRow(rowNumber, reason));
                continue;
            }
            try {
                QuestionDTO dto = toDto(row);
                Long questionId = questionService.createQuestion(dto);
                affectedScenarios.add(dto.getScenarioId());
                result.setSuccessCount(result.getSuccessCount() + 1);
                log.info("导入题目成功: id={}, row={}", questionId, rowNumber);
            } catch (BusinessException e) {
                result.setFailCount(result.getFailCount() + 1);
                result.getErrors().add(new ImportResultVO.ErrorRow(rowNumber, e.getMessage()));
            }
        }

        // 汇总所有受影响场景，统一清理缓存
        affectedScenarios.forEach(questionCacheService::clearScenarioCache);
        log.info("批量导入完成: total={}, success={}, fail={}",
                result.getTotal(), result.getSuccessCount(), result.getFailCount());
        return result;
    }

    @Override
    public void exportQuestions(HttpServletResponse response, Long scenarioId) {
        List<Question> questions = questionMapper.selectList(
                new LambdaQueryWrapper<Question>()
                        .eq(scenarioId != null, Question::getScenarioId, scenarioId)
                        .orderByAsc(Question::getSortOrder)
                        .orderByAsc(Question::getId));

        List<QuestionExportRow> rows = questions.stream().map(q -> {
            QuestionExportRow row = new QuestionExportRow();
            row.setId(q.getId());
            row.setScenarioId(q.getScenarioId());
            row.setContent(q.getContent());
            row.setType(q.getType());
            row.setDifficulty(q.getDifficulty());
            row.setExpectedAnswer(q.getExpectedAnswer());
            row.setKeywords(q.getKeywords());
            row.setTags(String.join(",", questionService.getTagCodes(q.getId())));
            return row;
        }).collect(Collectors.toList());

        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("题库导出", StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition",
                    "attachment;filename*=utf-8''" + fileName + ".xlsx");
            EasyExcel.write(response.getOutputStream(), QuestionExportRow.class)
                    .sheet("题库")
                    .doWrite(rows);
        } catch (IOException e) {
            log.error("导出题库失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    private String validateRow(QuestionImportRow row) {
        if (row == null) {
            return "空行";
        }
        if (row.getScenarioId() == null) {
            return "场景ID不能为空";
        }
        if (row.getContent() == null || row.getContent().isBlank()) {
            return "题目内容不能为空";
        }
        if (row.getContent().trim().length() < 10) {
            return "题目内容长度不足10个字符";
        }
        if (row.getDifficulty() == null || row.getDifficulty() < 1 || row.getDifficulty() > 3) {
            return "难度必须为 1、2、3";
        }
        if (row.getType() != null && !row.getType().isBlank()
                && !ALLOWED_TYPES.contains(row.getType().trim())) {
            return "题型必须为 technical、behavioral、hr";
        }
        return null;
    }

    private QuestionDTO toDto(QuestionImportRow row) {
        QuestionDTO dto = new QuestionDTO();
        dto.setScenarioId(row.getScenarioId());
        dto.setContent(row.getContent().trim());
        dto.setType(row.getType() == null || row.getType().isBlank()
                ? "technical" : row.getType().trim());
        dto.setDifficulty(row.getDifficulty());
        dto.setExpectedAnswer(row.getExpectedAnswer());
        dto.setKeywords(row.getKeywords());
        if (row.getTags() != null && !row.getTags().isBlank()) {
            dto.setTags(Arrays.stream(row.getTags().split("[,，、]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList()));
        }
        return dto;
    }
}