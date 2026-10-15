package com.iflytek.interview.common.dto;

import lombok.Data;
import java.util.List;

/**
 * Python /api/ai/asr 的响应结构。
 * text 为完整转写文本，segments 为带起止时间的转写分段。
 */
@Data
public class AsrResponse {
    private String text;
    private String language;
    private List<AsrSegment> segments;

    @Data
    public static class AsrSegment {
        private double start;
        private double end;
        private String text;
    }
}
