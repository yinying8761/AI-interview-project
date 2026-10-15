package com.iflytek.interview.interview.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RecordScoreDTO {

    @NotNull(message = "评分不能为空")
    @DecimalMin(value = "0.00", message = "评分不能低于0分")
    @DecimalMax(value = "100.00", message = "评分不能高于100分")
    private BigDecimal score;

    @Size(max = 2000, message = "评语不能超过2000个字符")
    private String aiFeedback;
}
