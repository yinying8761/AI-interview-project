package com.iflytek.interview.question.vo;

import com.iflytek.interview.question.entity.QuestionPaper;
import lombok.Data;

import java.util.List;

/**
 * 试卷详情：包含试卷主记录与题目明细。
 */
@Data
public class PaperDetailVO {

    private QuestionPaper paper;
    private List<QuestionVO> questions;
}