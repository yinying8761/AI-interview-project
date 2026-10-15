package com.iflytek.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面试视频帧。
 * 记录某一时刻从视频轨道抽取的一帧画面及其质量指标，
 * 通过 recordId + questionIndex + frameSeq 唯一定位，供表情分析与质量分析使用。
 */
@Data
@TableName("interview_video_frame")
public class InterviewVideoFrame {

    /** 主键，由数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 面试会话 ID，定位到 interview_record */
    private Long recordId;

    /** 采集人，即当前面试用户 */
    private Long userId;

    /** 题目顺序 */
    private Integer questionIndex;

    /** 帧序号，同一题目内从 1 递增 */
    private Long frameSeq;

    /** 是否关键帧：1 是、0 否 */
    private Integer keyFrame;

    /** 相对会话开始的时间戳（毫秒），用于多模态对齐 */
    private Long timestampMs;

    /** 实际捕获时间 */
    private LocalDateTime capturedAt;

    /** 图像宽度（像素） */
    private Integer width;

    /** 图像高度（像素） */
    private Integer height;

    /** 亮度指标，取值 0～1 */
    private BigDecimal brightness;

    /** 清晰度指标 */
    private BigDecimal sharpness;

    /** 画面变化率，用于关键帧判断 */
    private BigDecimal diffRatio;

    /** 帧图像文件地址 */
    private String fileUrl;

    /** 缩略图地址 */
    private String thumbnailUrl;

    /** 图像格式，例如 image/jpeg */
    private String mimeType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 状态：CREATED / UPLOADED / ANALYZED */
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
