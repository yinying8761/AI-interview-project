package com.iflytek.interview.question.service;

import com.iflytek.interview.question.entity.QuestionPaper;
import com.iflytek.interview.question.vo.QuestionVO;

import java.util.List;

/**
 * 试卷查询能力：供面试流程获取固定试卷题目。
 */
public interface PaperQueryService {

    QuestionPaper getPaper(Long paperId);

    /** 返回某场景下可用的试卷（GENERATED/ENABLED）。 */
    List<QuestionPaper> listAvailablePapers(Long scenarioId);

    /** 返回某试卷的题目明细（候选人视角）。 */
    List<QuestionVO> getPaperQuestions(Long paperId);

    /** 会话取题：绑定试卷则取试卷题目，否则随机取指定数量题目。 */
    List<QuestionVO> getSessionQuestions(Long scenarioId, Long paperId, Integer count);

    /** 校验试卷存在、归属场景匹配且状态可用。 */
    void validatePaper(Long paperId, Long scenarioId);
}