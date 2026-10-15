package com.iflytek.interview.interview.dto;

import lombok.Data;

/**
 * 创建视频片段请求。
 * 开始录制某道题时调用，服务端登记一个状态为 RECORDING 的视频片段。
 */
@Data
public class VideoSegmentCreateRequest {

    /** 面试会话 ID */
    private Long recordId;

    /** 题目顺序 */
    private Integer questionIndex;

    /** 视频格式，例如 video/webm;codecs=vp8,opus */
    private String mimeType;

    /** 视频宽度（像素） */
    private Integer width;

    /** 视频高度（像素） */
    private Integer height;

    /** 帧率 */
    private Integer frameRate;

    /** 码率 */
    private Integer bitrate;

    /** 采集时的网络等级 */
    private String networkLevel;
}
