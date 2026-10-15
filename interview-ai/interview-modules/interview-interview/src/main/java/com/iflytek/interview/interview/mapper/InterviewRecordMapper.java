package com.iflytek.interview.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.interview.interview.entity.InterviewRecord;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface InterviewRecordMapper extends BaseMapper<InterviewRecord> {

    /**
     * 管理员和面试官查看全部记录时关联候选人基础信息。
     */
    @Select("""
            SELECT r.*, u.username, u.nickname
            FROM interview_record r
            LEFT JOIN sys_user u ON u.id = r.user_id
            ORDER BY r.created_at DESC
            """)
    List<InterviewRecord> selectAllWithCandidate();
}
