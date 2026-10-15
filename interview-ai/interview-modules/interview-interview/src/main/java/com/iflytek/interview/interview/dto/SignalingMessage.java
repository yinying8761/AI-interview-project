package com.iflytek.interview.interview.dto;

import lombok.Data;

/**
 * 信令消息：客户端与服务端通过 WebSocket 交换的统一消息结构。
 * type 取值约定见讲义 3.2：
 * JOIN / ROOM_STATE / PEER_JOINED / OFFER / ANSWER / ICE_CANDIDATE /
 * MEDIA_STATE / LEAVE / PEER_LEFT / ERROR。
 */
@Data
public class SignalingMessage {

    /** 消息类型，取值见类注释约定 */
    private String type;

    /** 面试会话 ID，用于定位房间并校验归属 */
    private Long recordId;

    /** 房间编号，与 interview_record.room_code 二次校验 */
    private String roomCode;

    /** 角色：HOST 或 CANDIDATE */
    private String role;

    /** 发送方用户 ID，服务端在转发时填充 */
    private String fromUserId;

    /** 目标用户 ID；为空表示广播给同房间其他成员 */
    private String toUserId;

    /** 消息载荷，JSON 字符串，用于承载 SDP、ICE Candidate 或媒体状态 */
    private String payload;
}
