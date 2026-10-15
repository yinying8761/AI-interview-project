package com.iflytek.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("interview_scenario")
public class Scenario implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String techField;
    private Integer difficulty;
    private String coverImage;
    private Integer questionCount;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
