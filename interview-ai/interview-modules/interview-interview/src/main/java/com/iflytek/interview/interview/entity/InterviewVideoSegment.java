package com.iflytek.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试视频片段。
 * 记录按题目与时间窗口录制的一段视频文件及其采集参数，
 * 通过 recordId + questionIndex + segmentSeq 唯一定位，供回放与评分使用。
 */
@Data
@TableName("interview_video_segment")
public class InterviewVideoSegment {

    /** 主键，由数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 面试会话 ID */
    private Long recordId;

    /** 采集人 */
    private Long userId;

    /** 题目顺序 */
    private Integer questionIndex;

    /** 片段序号，同一题目内从 1 递增 */
    private Integer segmentSeq;

    /** 片段开始时间 */
    private LocalDateTime startAt;

    /** 片段结束时间，上传完成时回填 */
    private LocalDateTime endAt;

    /** 片段时长（毫秒） */
    private Integer durationMs;

    /** 视频文件地址 */
    private String fileUrl;

    /** 封面缩略图地址 */
    private String thumbnailUrl;

    /** 视频格式，例如 video/webm;codecs=vp8,opus */
    private String mimeType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 视频宽度（像素） */
    private Integer width;

    /** 视频高度（像素） */
    private Integer height;

    /** 帧率 */
    private Integer frameRate;

    /** 码率 */
    private Integer bitrate;

    /** 采集时的网络等级，例如 GOOD / NORMAL / POOR */
    private String networkLevel;

    /** 状态：RECORDING / UPLOADED */
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
