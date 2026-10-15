package com.iflytek.interview.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.interview.interview.entity.InterviewVideoFrame;
import org.apache.ibatis.annotations.Mapper;

/**
 * 视频帧 Mapper。
 * 继承 MyBatis-Plus BaseMapper，复用 interview_video_frame 表通用增删改查，
 * 无需编写 XML；@MapperScan("com.iflytek.interview.**.mapper") 已覆盖该包。
 */
@Mapper
public interface InterviewVideoFrameMapper
        extends BaseMapper<InterviewVideoFrame> {
}
