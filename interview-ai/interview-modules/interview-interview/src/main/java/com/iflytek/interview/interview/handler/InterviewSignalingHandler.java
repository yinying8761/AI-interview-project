package com.iflytek.interview.interview.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.interview.dto.SignalingMember;
import com.iflytek.interview.interview.dto.SignalingMessage;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.service.InterviewRecordService;
import com.iflytek.interview.interview.service.InterviewSignalingRoomService;
import com.iflytek.interview.interview.service.InterviewSignalingValidateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 信令处理器：接收客户端消息，按类型分发处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewSignalingHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final InterviewSignalingRoomService roomService;
    private final InterviewSignalingValidateService validateService;
    private final InterviewRecordService interviewRecordService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 连接建立：登记会话，后续按 sessionId 定位
        WebSocketSessionRegistry.add(session);
        log.info("WebSocket连接建立: sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            // 反序列化客户端消息
            SignalingMessage request = objectMapper.readValue(
                    message.getPayload(), SignalingMessage.class);

            // 每次收到消息都刷新该成员的活跃时间
            roomService.updateActiveTime(session.getId());

            switch (request.getType()) {
                case "JOIN" -> handleJoin(session, request);
                case "OFFER", "ANSWER", "ICE_CANDIDATE", "MEDIA_STATE" ->
                        forwardToRoom(session, request);
                case "LEAVE" -> handleLeave(session);
                default -> sendError(session, "不支持的信令类型: " + request.getType());
            }
        } catch (Exception e) {
            log.error("处理信令消息失败", e);
            sendError(session, "信令处理失败: " + e.getMessage());
        }
    }

    /**
     * 处理 JOIN：校验会话后登记房间成员，返回 ROOM_STATE、通知对端并写加入事件。
     */
    private void handleJoin(WebSocketSession session,
                            SignalingMessage request) throws Exception {
        // 握手阶段写入的 userId / roles（WebSocket 线程没有 SecurityContext，需从 attributes 取）
        Long userId = (Long) session.getAttributes().get("userId");
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) session.getAttributes().get("roles");

        if (userId == null) {
            sendError(session, "用户未认证");
            return;
        }

        // 校验会话、房间、状态与角色
        InterviewRecord record = validateService.validateJoin(
                userId, request.getRecordId(), request.getRoomCode(), request.getRole(), roles);

        // 主持人加入时自动绑定 interviewer_id（与第05讲 join(role=HOST) 行为一致）
        if ("HOST".equals(request.getRole()) && record.getInterviewerId() == null) {
            record.setInterviewerId(userId);
            interviewRecordService.updateById(record);
        }

        // 组装并登记成员
        SignalingMember member = new SignalingMember();
        member.setSessionId(session.getId());
        member.setUserId(userId);
        member.setRecordId(request.getRecordId());
        member.setRoomCode(record.getRoomCode());
        member.setRole(request.getRole());

        roomService.join(request.getRecordId(), record.getRoomCode(), member);

        // 返回房间状态：会话信息 + 已在线成员（不含自己）
        List<SignalingMember> others = roomService.listOtherMembers(
                request.getRecordId(), record.getRoomCode(), session.getId());

        Map<String, Object> roomState = new HashMap<>();
        roomState.put("recordId", record.getId());
        roomState.put("roomCode", record.getRoomCode());
        roomState.put("status", record.getStatus());
        roomState.put("members", others);

        send(session, "ROOM_STATE", objectMapper.writeValueAsString(roomState));

        // 通知对端“新成员加入”，对端据此发起 Offer 或等待 Offer
        for (SignalingMember other : others) {
            WebSocketSession otherSession = WebSocketSessionRegistry.find(other.getSessionId());

            if (otherSession != null && otherSession.isOpen()) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("userId", userId);
                payload.put("role", request.getRole());

                send(otherSession, "PEER_JOINED",
                        objectMapper.writeValueAsString(payload));
            }
        }

        // 写入加入事件，供后续回放 / 审计
        String event = "HOST".equals(request.getRole())
                ? "HOST_JOINED" : "CANDIDATE_JOINED";

        interviewRecordService.saveEvent(record.getId(), userId, event, null);
    }

    /**
     * 转发信令：仅把 OFFER/ANSWER/ICE_CANDIDATE/MEDIA_STATE 转发给同房间其他成员。
     */
    private void forwardToRoom(WebSocketSession session,
                               SignalingMessage request) throws Exception {
        // 成员必须已加入房间
        SignalingMember current = roomService.findBySession(session);

        if (current == null) {
            sendError(session, "尚未加入面试房间");
            return;
        }

        // 防止跨房间转发
        validateService.validateRoomMember(
                current, request.getRecordId(), request.getRoomCode());

        // 标记发送方，前端可据此判断消息来源
        request.setFromUserId(String.valueOf(current.getUserId()));

        List<SignalingMember> others = roomService.listOtherMembers(
                current.getRecordId(), current.getRoomCode(), session.getId());

        // 有 toUserId 则单发，否则广播给其他成员
        for (SignalingMember other : others) {
            boolean shouldSend = request.getToUserId() == null
                    || request.getToUserId().equals(String.valueOf(other.getUserId()));

            if (shouldSend) {
                WebSocketSession target = WebSocketSessionRegistry.find(other.getSessionId());

                if (target != null && target.isOpen()) {
                    send(target, request.getType(), request.getPayload());
                }
            }
        }
    }

    /**
     * 处理 LEAVE：移除成员、通知对端离开并写 PEER_LEFT 事件。
     */
    private void handleLeave(WebSocketSession session) {
        SignalingMember member = roomService.leave(session);

        if (member != null) {
            notifyPeerLeft(member);
            interviewRecordService.saveEvent(
                    member.getRecordId(), member.getUserId(), "PEER_LEFT", null);
        }
    }

    /** 通知同房间其他成员：某人已离开。 */
    private void notifyPeerLeft(SignalingMember member) {
        try {
            List<SignalingMember> others = roomService.listOtherMembers(
                    member.getRecordId(), member.getRoomCode(), member.getSessionId());

            for (SignalingMember other : others) {
                WebSocketSession target = WebSocketSessionRegistry.find(other.getSessionId());

                if (target != null && target.isOpen()) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("userId", member.getUserId());
                    payload.put("role", member.getRole());

                    send(target, "PEER_LEFT",
                            objectMapper.writeValueAsString(payload));
                }
            }
        } catch (Exception e) {
            log.warn("通知成员离开失败", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      CloseStatus status) {
        // 连接关闭兜底：移除注册表并触发 leave，保证房间成员被清理
        WebSocketSessionRegistry.remove(session.getId());
        handleLeave(session);
        log.info("WebSocket连接关闭: sessionId={}, status={}", session.getId(), status);
    }

    /** 发送一条信令消息：包装为 {type, payload} 再序列化发送。 */
    private void send(WebSocketSession session, String type, String payload)
            throws IOException {
        SignalingMessage message = new SignalingMessage();
        message.setType(type);
        message.setPayload(payload);

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
    }

    /** 发送错误消息到客户端。 */
    private void sendError(WebSocketSession session, String message) {
        try {
            send(session, "ERROR",
                    objectMapper.writeValueAsString(Map.of("message", message)));
        } catch (Exception e) {
            log.warn("发送WebSocket错误消息失败", e);
        }
    }
}
