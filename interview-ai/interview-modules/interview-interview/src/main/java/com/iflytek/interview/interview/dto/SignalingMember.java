package com.iflytek.interview.interview.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 房间成员：记录一个已加入房间的 WebSocket 会话在房间内的身份。
 */
@Data
public class SignalingMember {

    /** WebSocket 会话 ID，作为成员在房间内的唯一标识 */
    private String sessionId;

    /** 用户 ID */
    private Long userId;

    /** 面试会话 ID */
    private Long recordId;

    /** 房间编号 */
    private String roomCode;

    /** 角色：HOST 或 CANDIDATE */
    private String role;

    /** 加入时间 */
    private LocalDateTime joinedAt;

    /** 最近活跃时间，用于心跳或超时清理 */
    private LocalDateTime lastActiveAt;
}
