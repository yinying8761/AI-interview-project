package com.iflytek.interview.question.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入结果：记录成功与失败明细，便于前端展示。
 */
@Data
public class ImportResultVO {

    private int total;
    private int successCount;
    private int failCount;
    private List<ErrorRow> errors = new ArrayList<>();

    public static class ErrorRow {
        private int row;
        private String reason;

        public ErrorRow() {
        }

        public ErrorRow(int row, String reason) {
            this.row = row;
            this.reason = reason;
        }

        public int getRow() {
            return row;
        }

        public void setRow(int row) {
            this.row = row;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}