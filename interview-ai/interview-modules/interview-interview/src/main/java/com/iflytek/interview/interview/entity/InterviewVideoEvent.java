package com.iflytek.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试视频事件。
 * 记录视频采集过程的关键节点（帧保存、关键帧选择、片段创建、片段上传等），
 * 用于回放与审计，可与音频事件、会话事件共用同一时间轴。
 */
@Data
@TableName("interview_video_event")
public class InterviewVideoEvent {

    /** 主键，由数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 面试会话 ID */
    private Long recordId;

    /** 关联的视频片段 ID */
    private Long segmentId;

    /** 关联的视频帧 ID */
    private Long frameId;

    /** 题目顺序 */
    private Integer questionIndex;

    /** 事件类型，例如 VIDEO_KEY_FRAME_SELECTED、VIDEO_SEGMENT_CREATED */
    private String eventType;

    /** 操作人 */
    private Long operatorId;

    /** 事件发生时间 */
    private LocalDateTime eventTime;

    /** 事件载荷，JSON 字符串，保存附加信息 */
    private String payloadJson;

    private LocalDateTime createdAt;
}
