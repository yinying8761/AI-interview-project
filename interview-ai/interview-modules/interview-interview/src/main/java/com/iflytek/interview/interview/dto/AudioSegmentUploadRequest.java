package com.iflytek.interview.interview.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 登记片段上传完成请求。
 * 音频文件上传到对象存储后，前端调用该接口回填文件地址、时长与音量指标。
 */
@Data
public class AudioSegmentUploadRequest {

    /** 已创建的音频片段 ID */
    private Long segmentId;

    /** 音频文件对象存储地址 */
    private String fileUrl;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 片段时长（毫秒） */
    private Integer durationMs;

    /** 平均音量（RMS） */
    private BigDecimal avgVolume;

    /** 最大音量（RMS） */
    private BigDecimal maxVolume;

    /** 静音比例，取值 0～1 */
    private BigDecimal silenceRatio;
}
