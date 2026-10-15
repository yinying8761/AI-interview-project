package com.iflytek.interview.interview.service;

import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.interview.dto.SignalingMember;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 信令房间管理服务：以 recordId:roomCode 为键维护一个房间内的成员集合。
 * 单房间最多两人（候选人 + 主持人），同角色只允许一人在线。
 */
@Slf4j
@Service
public class InterviewSignalingRoomService {

    // 外层键为房间键 recordId:roomCode，内层键为 WebSocket 会话 ID -> 成员
    private final Map<String, Map<String, SignalingMember>> rooms =
            new ConcurrentHashMap<>();

    /**
     * 加入房间：创建或复用房间，校验人数与角色唯一性后登记成员。
     * @param recordId  面试会话 ID
     * @param roomCode  房间编号
     * @param member    要加入的成员（sessionId、userId、role 已填充）
     */
    public void join(Long recordId, String roomCode, SignalingMember member) {
        // 取出或创建该房间；computeIfAbsent 保证并发下只创建一次
        Map<String, SignalingMember> room = rooms.computeIfAbsent(
                buildRoomKey(recordId, roomCode),
                key -> new ConcurrentHashMap<>());

        // 房间已满两人则拒绝，避免第三方进入
        if (room.size() >= 2) {
            throw new BusinessException(400, "面试房间成员已满");
        }

        // 同角色只能一人在线，防止两名候选人或两名主持人同时在线
        boolean sameRoleExists = room.values().stream()
                .anyMatch(item -> item.getRole().equals(member.getRole()));

        if (sameRoleExists) {
            throw new BusinessException(400, "相同角色已在线");
        }

        member.setJoinedAt(LocalDateTime.now());
        member.setLastActiveAt(LocalDateTime.now());
        room.put(member.getSessionId(), member);

        log.info("加入信令房间: recordId={}, userId={}, role={}, sessionId={}",
                recordId, member.getUserId(), member.getRole(), member.getSessionId());
    }

    /**
     * 离开房间：按 WebSocket 会话 ID 找到并移除成员；房间清空后删除房间。
     * @return 被移除的成员，若不存在返回 null
     */
    public SignalingMember leave(WebSocketSession session) {
        for (Map.Entry<String, Map<String, SignalingMember>> roomEntry : rooms.entrySet()) {
            Map<String, SignalingMember> room = roomEntry.getValue();
            SignalingMember member = room.remove(session.getId());

            if (member != null) {
                log.info("离开信令房间: recordId={}, userId={}, sessionId={}",
                        member.getRecordId(), member.getUserId(), session.getId());

                // 房间无人则移除，避免内存泄漏
                if (room.isEmpty()) {
                    rooms.remove(roomEntry.getKey());
                }

                return member;
            }
        }

        return null;
    }

    /**
     * 列出同房间内除指定会话外的其他成员，用于信令转发与“新成员加入”通知。
     */
    public List<SignalingMember> listOtherMembers(Long recordId,
                                                  String roomCode,
                                                  String excludeSessionId) {
        Map<String, SignalingMember> room = rooms.getOrDefault(
                buildRoomKey(recordId, roomCode),
                Collections.emptyMap());

        return room.values().stream()
                .filter(item -> !item.getSessionId().equals(excludeSessionId))
                .collect(Collectors.toList());
    }

    /** 根据 WebSocket 会话反查成员，用于信令转发前确认身份。 */
    public SignalingMember findBySession(WebSocketSession session) {
        for (Map<String, SignalingMember> room : rooms.values()) {
            SignalingMember member = room.get(session.getId());
            if (member != null) {
                return member;
            }
        }

        return null;
    }

    /** 更新成员最近活跃时间，作为心跳 / 超时清理依据。 */
    public void updateActiveTime(String websocketSessionId) {
        rooms.values().forEach(room -> {
            SignalingMember member = room.get(websocketSessionId);
            if (member != null) {
                member.setLastActiveAt(LocalDateTime.now());
            }
        });
    }

    /** 组装房间键 recordId:roomCode，保证跨会话记录的不同房间互不干扰。 */
    private String buildRoomKey(Long recordId, String roomCode) {
        return recordId + ":" + roomCode;
    }
}
