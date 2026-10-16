package com.iflytek.interview.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.interview.interview.entity.InterviewRecord;
import org.apache.ibatis.annotations.Param;
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

    /**
     * 面试官查看自己发布的正式面试记录（模拟面试不可见）。
     */
    @Select("""
            SELECT r.*, u.username, u.nickname
            FROM interview_record r
            LEFT JOIN sys_user u ON u.id = r.user_id
            WHERE r.interview_type = 'FORMAL'
              AND r.interviewer_id = #{interviewerId}
            ORDER BY r.created_at DESC
            """)
    List<InterviewRecord> selectFormalForInterviewer(@Param("interviewerId") Long interviewerId);

    @Select("SELECT * FROM interview_record WHERE id = #{id} FOR UPDATE")
    InterviewRecord selectForUpdate(Long id);
}
