package com.iflytek.interview.question.service;

import com.iflytek.interview.question.vo.ImportResultVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 题库批量导入导出能力。
 */
public interface QuestionImportService {

    ImportResultVO importQuestions(MultipartFile file);

    void exportQuestions(HttpServletResponse response, Long scenarioId);
}