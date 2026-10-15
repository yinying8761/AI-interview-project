package com.iflytek.interview.question.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iflytek.interview.question.dto.QuestionTagDTO;
import com.iflytek.interview.question.entity.QuestionTag;

import java.util.List;

public interface QuestionTagService extends IService<QuestionTag> {

    List<QuestionTag> listTags(String type);

    QuestionTag createTag(QuestionTagDTO dto);

    void updateTag(Long id, QuestionTagDTO dto);

    void disableTag(Long id);
}