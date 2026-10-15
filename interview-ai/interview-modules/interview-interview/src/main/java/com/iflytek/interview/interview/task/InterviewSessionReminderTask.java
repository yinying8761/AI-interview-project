package com.iflytek.interview.interview.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.entity.InterviewStatus;
import com.iflytek.interview.interview.mapper.InterviewRecordMapper;
import com.iflytek.interview.interview.service.InterviewRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 第05讲：临期面试提醒。扫描 15 分钟内待开始且未提醒的会话，更新提醒状态并写入提醒事件。
 */
@Slf4j
@Component
public class InterviewSessionReminderTask {

    @Autowired
    private InterviewRecordMapper recordMapper;

    @Autowired
    private InterviewRecordService sessionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Scheduled(fixedRate = 60_000, initialDelay = 30_000)
    public void remindUpcomingSessions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusMinutes(15);

        List<InterviewRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<InterviewRecord>()
                        .eq(InterviewRecord::getStatus, InterviewStatus.PENDING.getCode())
                        .eq(InterviewRecord::getReminderSent, 0)
                        .isNotNull(InterviewRecord::getScheduledAt)
                        .between(InterviewRecord::getScheduledAt, now, deadline));

        for (InterviewRecord record : records) {
            record.setReminderSent(1);
            recordMapper.updateById(record);

            Map<String, Object> payload = new HashMap<>();
            payload.put("scheduledAt", record.getScheduledAt().toString());
            payload.put("message", "面试将于15分钟内开始");
            sessionService.saveEvent(record.getId(), null,
                    "REMINDER_SENT", toJson(payload));
        }

        if (!records.isEmpty()) {
            log.info("面试提醒处理完成: count={}", records.size());
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }
}