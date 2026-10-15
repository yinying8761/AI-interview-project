package com.iflytek.interview.interview.dto;

import lombok.Data;

/**
 * 登记视频片段上传完成请求。
 * 视频文件上传到对象存储后，前端回填文件地址、缩略图与时长。
 */
@Data
public class VideoSegmentUploadRequest {

    /** 已创建的片段 ID */
    private Long segmentId;

    /** 视频文件地址 */
    private String fileUrl;

    /** 封面缩略图地址 */
    private String thumbnailUrl;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 片段时长（毫秒） */
    private Integer durationMs;
}
