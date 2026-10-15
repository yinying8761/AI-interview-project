package com.iflytek.interview.interview.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.mapper.InterviewRecordMapper;
import com.iflytek.interview.interview.service.ReportGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务（第11讲）：自动处理超时未结束的面试。
 */
@Slf4j
@Component
public class InterviewScheduledTask {

    private static final int TIMEOUT_MINUTES = 60;

    @Autowired
    private InterviewRecordMapper recordMapper;

    @Autowired
    private ReportGenerator reportGenerator;

    @Scheduled(fixedRate = 60_000, initialDelay = 30_000)
    public void timeoutInterviewAutoSubmit() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        List<InterviewRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<InterviewRecord>()
                        .eq(InterviewRecord::getStatus, "ongoing")
                        .isNotNull(InterviewRecord::getStartedAt)
                        .lt(InterviewRecord::getStartedAt, threshold)
        );

        if (records.isEmpty()) {
            return;
        }

        log.info("[定时] 发现 {} 个超时面试，自动提交", records.size());
        for (InterviewRecord record : records) {
            record.setStatus("completed");
            record.setAnswerData("(timeout auto submit)");
            record.setCompletedAt(LocalDateTime.now());
            recordMapper.updateById(record);
            reportGenerator.generateReport(record.getId());
        }
    }
}
