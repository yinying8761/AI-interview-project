package com.iflytek.interview.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.interview.interview.entity.InterviewAudioEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 音频事件 Mapper。
 * 提供 interview_audio_event 表的通用增删改查。
 */
@Mapper
public interface InterviewAudioEventMapper
        extends BaseMapper<InterviewAudioEvent> {
}
