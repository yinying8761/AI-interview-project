package com.iflytek.interview.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.interview.interview.entity.InterviewVideoSegment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 视频片段 Mapper，提供 interview_video_segment 表通用增删改查。
 */
@Mapper
public interface InterviewVideoSegmentMapper
        extends BaseMapper<InterviewVideoSegment> {
}
