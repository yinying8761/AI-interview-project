package com.iflytek.interview.interview.dto;

import lombok.Data;

/**
 * 登记视频帧上传完成请求。
 * 帧图像上传到对象存储后，前端回填文件地址与缩略图地址。
 */
@Data
public class VideoFrameUploadRequest {

    /** 已创建的帧 ID */
    private Long frameId;

    /** 帧图像文件地址 */
    private String fileUrl;

    /** 缩略图地址 */
    private String thumbnailUrl;

    /** 文件大小（字节） */
    private Long fileSize;
}
