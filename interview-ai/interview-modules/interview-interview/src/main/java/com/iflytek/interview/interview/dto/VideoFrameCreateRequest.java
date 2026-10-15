package com.iflytek.interview.interview.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建视频帧请求。
 * 前端抽帧后调用，服务端登记一帧画面及其质量指标，状态为 CREATED。
 */
@Data
public class VideoFrameCreateRequest {

    /** 面试会话 ID */
    private Long recordId;

    /** 题目顺序 */
    private Integer questionIndex;

    /** 相对会话开始的时间戳（毫秒） */
    private Long timestampMs;

    /** 图像宽度（像素） */
    private Integer width;

    /** 图像高度（像素） */
    private Integer height;

    /** 亮度指标 */
    private BigDecimal brightness;

    /** 清晰度指标 */
    private BigDecimal sharpness;

    /** 画面变化率 */
    private BigDecimal diffRatio;

    /** 是否关键帧 */
    private Boolean keyFrame;

    /** 图像格式，例如 image/jpeg */
    private String mimeType;
}
