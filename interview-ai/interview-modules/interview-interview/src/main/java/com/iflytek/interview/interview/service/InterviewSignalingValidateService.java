package com.iflytek.interview.interview.service;

import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.interview.dto.SignalingMember;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.entity.InterviewStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 信令校验服务：负责“能否进入房间”与“信令能否转发”的规则判断。
 */
@Service
@RequiredArgsConstructor
public class InterviewSignalingValidateService {

    private final InterviewRecordService interviewRecordService;

    /**
     * 进入房间校验：校验会话存在、房间编号一致、状态允许加入、角色与用户匹配。
     * @param userId    当前 WebSocket 用户
     * @param recordId  目标面试会话
     * @param roomCode  客户端携带的房间编号
     * @param role      HOST 或 CANDIDATE
     * @return 校验通过的 InterviewRecord，供后续组装房间状态
     */
    public InterviewRecord validateJoin(Long userId,
                                        Long recordId,
                                        String roomCode,
                                        String role,
                                        List<String> roles) {
        // WebSocket 线程没有 SecurityContext，这里直接用 getById 查记录，避免依赖第05讲 getSession
        //（第05讲 getSession 内部用 SecurityUtil.hasRole，在信令线程会误判为未登录）。
        InterviewRecord record = interviewRecordService.getById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }

        // 补齐房间编号：若记录缺少 roomCode（如旧流程创建的记录），生成并持久化
        String recordRoom = record.getRoomCode();
        if (recordRoom == null || recordRoom.isBlank()) {
            recordRoom = generateRoomCode();
            record.setRoomCode(recordRoom);
            interviewRecordService.updateById(record);
        }

        // 房间编号二次校验：客户端未携带时以记录为准，携带时两者必须一致
        if (roomCode != null && !roomCode.isBlank() && !recordRoom.equals(roomCode)) {
            throw new BusinessException(403, "房间编号与会话不一致");
        }

        // 只有待开始 / 进行中的会话允许进入实时视频面试间
        if (!InterviewStatus.PENDING.getCode().equals(record.getStatus())
                && !InterviewStatus.ONGOING.getCode().equals(record.getStatus())) {
            throw new BusinessException(400, "当前会话状态无法进入视频面试间");
        }

        // 校验角色：候选人必须是该会话的本人；主持人必须是面试官/管理员，且只能是绑定的主持人（未绑定时允许）
        boolean isAdmin = roles != null && roles.contains("admin");
        boolean isInterviewer = roles != null && roles.contains("interviewer");
        if ("CANDIDATE".equals(role)) {
            if (!record.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        } else if ("HOST".equals(role)) {
            if (!isAdmin && !isInterviewer) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            if (record.getInterviewerId() != null && !record.getInterviewerId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        return record;
    }

    /**
     * 信令转发校验：确认成员与服务端的 recordId / roomCode 一致，防止跨房间转发。
     */
    public void validateRoomMember(SignalingMember member,
                                   Long targetRecordId,
                                   String targetRoomCode) {
        if (!member.getRecordId().equals(targetRecordId)
                || !member.getRoomCode().equals(targetRoomCode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /** 生成房间编号：room- 前缀 + 8 位随机串。 */
    private String generateRoomCode() {
        return "room-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
