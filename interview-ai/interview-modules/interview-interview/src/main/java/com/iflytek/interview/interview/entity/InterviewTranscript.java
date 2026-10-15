package com.iflytek.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 转写结果主表：保存 Python /api/ai/asr 返回的完整文本与分段。
 */
@Data
@TableName("interview_transcript")
public class InterviewTranscript {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 面试记录 ID。 */
    private Long recordId;

    /** 题号。 */
    private Integer questionIndex;

    /** 音频片段 ID（对应 interview_audio_segment.id）。 */
    private Long segmentId;

    /** 完整转写文本。 */
    private String text;

    /** 语言。 */
    private String language;

    /** 转写分段 JSON（含起止时间）。 */
    private String segmentsJson;

    /** 状态：PARSED。 */
    private String status;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
