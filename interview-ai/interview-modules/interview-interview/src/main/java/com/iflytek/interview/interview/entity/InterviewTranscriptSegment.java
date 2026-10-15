package com.iflytek.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 转写分段表：每个分段的起止时间与文本。
 */
@Data
@TableName("interview_transcript_segment")
public class InterviewTranscriptSegment {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属转写结果 ID。 */
    private Long transcriptId;

    /** 分段序号。 */
    private Integer seq;

    /** 开始时间（毫秒）。 */
    private Long startMs;

    /** 结束时间（毫秒）。 */
    private Long endMs;

    /** 分段文本。 */
    private String text;

    /** 置信度。 */
    private BigDecimal confidence;
}
