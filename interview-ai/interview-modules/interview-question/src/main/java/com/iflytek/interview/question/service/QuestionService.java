package com.iflytek.interview.question.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.iflytek.interview.question.dto.QuestionDTO;
import com.iflytek.interview.question.entity.Question;
import com.iflytek.interview.question.vo.QuestionVO;

import java.util.List;

public interface QuestionService extends IService<Question> {

    Long createQuestion(QuestionDTO dto);

    void updateQuestion(Long id, QuestionDTO dto);

    void deleteQuestion(Long id);

    List<Question> randomQuestions(Long scenarioId, int count);

    /** 管理端分页查询题目。 */
    Page<QuestionVO> pageQuery(long current, long size, Long scenarioId, Integer difficulty, String keyword);

    /** 查询题目绑定的标签编码，用于编辑回显。 */
    List<String> getTagCodes(Long questionId);
}