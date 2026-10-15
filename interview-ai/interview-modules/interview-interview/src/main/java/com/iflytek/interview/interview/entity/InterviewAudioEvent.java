package com.iflytek.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试音频采集事件。
 * 记录音频采集过程的关键节点（创建、上传、分析、失败等），
 * 为后续面试回放与审计提供可追溯的过程事件。
 */
@Data
@TableName("interview_audio_event")
public class InterviewAudioEvent {

    /** 主键，由数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 面试会话 ID，定位到 interview_record */
    private Long recordId;

    /** 关联的音频片段 ID */
    private Long segmentId;

    /** 题目顺序 */
    private Integer questionIndex;

    /** 事件类型，例如 AUDIO_SEGMENT_UPLOADED、AUDIO_SEGMENT_FAILED */
    private String eventType;

    /** 操作人，即触发事件的用户 */
    private Long operatorId;

    /** 事件发生时间 */
    private LocalDateTime eventTime;

    /** 事件载荷，JSON 字符串，保存失败原因、指标等附加信息 */
    private String payloadJson;

    private LocalDateTime createdAt;
}
