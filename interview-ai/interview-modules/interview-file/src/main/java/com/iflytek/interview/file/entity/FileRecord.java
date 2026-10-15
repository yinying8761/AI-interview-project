package com.iflytek.interview.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_record")
public class FileRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String originalName;
    private String objectName;
    private String url;
    private Long fileSize;
    private String contentType;
    private String module;
    private LocalDateTime createdAt;
}
