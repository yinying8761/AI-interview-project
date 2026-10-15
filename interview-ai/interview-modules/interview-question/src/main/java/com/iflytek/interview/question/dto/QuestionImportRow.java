package com.iflytek.interview.question.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 题库批量导入/导出的列模型。
 */
@Data
public class QuestionImportRow {

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