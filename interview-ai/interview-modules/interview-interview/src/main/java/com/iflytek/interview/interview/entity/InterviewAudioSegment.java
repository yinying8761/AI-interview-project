package com.iflytek.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面试音频片段。
 * 每一条记录对应当前题目下的一段音频，通过 recordId + questionIndex + segmentSeq 唯一定位。
 * 业务表只保存对象存储地址与采集元数据，音频文件本身位于 MinIO。
 */
@Data
@TableName("interview_audio_segment")
public class InterviewAudioSegment {

    /** 主键，由数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 面试会话 ID，定位到第05讲建立的 interview_record */
    private Long recordId;

    /** 采集人，即当前面试用户 */
    private Long userId;

    /** 题目顺序，与试卷或随机抽题中的 questionIndex 对应 */
    private Integer questionIndex;

    /** 同一题目内的片段序号，从 1 开始递增 */
    private Integer segmentSeq;

    /** 片段开始时间 */
    private LocalDateTime startAt;

    /** 片段结束时间，上传完成时回填 */
    private LocalDateTime endAt;

    /** 片段时长，单位毫秒 */
    private Integer durationMs;

    /** 音频文件对象存储地址 */
    private String fileUrl;

    /** 音频格式，例如 audio/webm;codecs=opus */
    private String mimeType;

    /** 文件大小，单位字节 */
    private Long fileSize;

    /** 采样率，例如 48000 */
    private Integer sampleRate;

    /** 声道数，面试场景通常为 1 */
    private Integer channels;

    /** 平均音量（RMS），用于静音与音量判断 */
    private BigDecimal avgVolume;

    /** 最大音量（RMS） */
    private BigDecimal maxVolume;

    /** 静音比例，取值 0～1 */
    private BigDecimal silenceRatio;

    /** 状态：RECORDING / UPLOADED / ANALYZED / FAILED */
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
