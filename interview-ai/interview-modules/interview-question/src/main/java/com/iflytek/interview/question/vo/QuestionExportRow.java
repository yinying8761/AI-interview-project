package com.iflytek.interview.question.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 题库导出行模型，供管理端评审与维护。
 */
@Data
public class QuestionExportRow {

    @ExcelProperty("题目ID")
    private Long id;

    @ExcelProperty("场景ID")
    private Long scenarioId;

    @ExcelProperty("题目内容")
    private String content;

    @ExcelProperty("题型")
    private String type;

    @ExcelProperty("难度")
    private Integer difficulty;

    @ExcelProperty("参考答案")
    private String expectedAnswer;

    @ExcelProperty("评分关键词")
    private String keywords;

    @ExcelProperty("标签编码")
    private String tags;
}