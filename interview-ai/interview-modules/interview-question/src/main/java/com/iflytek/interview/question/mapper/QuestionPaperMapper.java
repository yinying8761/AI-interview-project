package com.iflytek.interview.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.interview.question.entity.QuestionPaper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionPaperMapper extends BaseMapper<QuestionPaper> {
}