package com.iflytek.interview.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.interview.interview.entity.InterviewAudioSegment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 音频片段 Mapper。
 * 继承 MyBatis-Plus BaseMapper，直接复用 interview_audio_segment 表的通用增删改查，
 * 无需编写 XML；@MapperScan("com.iflytek.interview.**.mapper") 已覆盖该包。
 */
@Mapper
public interface InterviewAudioSegmentMapper
        extends BaseMapper<InterviewAudioSegment> {
}
