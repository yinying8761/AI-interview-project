package com.iflytek.interview.question.vo;

import com.iflytek.interview.question.entity.Question;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题目出参 VO：面向考生 / 前端列表、详情。
 *
 * expectedAnswer（参考答案）只在管理端返回，避免候选人提前看到标准答案；
 * keywords（评分关键词）属于候选人可见信息，可随题目一起返回。
 */
@Data
public class QuestionVO {

    private Long id;
    private Long scenarioId;
    private String content;
    private String type;
    private Integer difficulty;
    private String expectedAnswer;
    private String keywords;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 实体 → VO（只拷贝考生该看的字段） */
    public static QuestionVO from(Question q) {
        QuestionVO vo = new QuestionVO();
        vo.setId(q.getId());
        vo.setScenarioId(q.getScenarioId());
        vo.setContent(q.getContent());
        vo.setType(q.getType());
        vo.setDifficulty(q.getDifficulty());
        vo.setKeywords(q.getKeywords());
        vo.setSortOrder(q.getSortOrder());
        vo.setCreatedAt(q.getCreatedAt());
        return vo;
    }

    /** 实体 → 管理端 VO：额外包含参考答案等完整题库字段 */
    public static QuestionVO fromForManagement(Question q) {
        QuestionVO vo = from(q);
        vo.setExpectedAnswer(q.getExpectedAnswer());
        vo.setStatus(q.getStatus());
        vo.setUpdatedAt(q.getUpdatedAt());
        return vo;
    }
}
