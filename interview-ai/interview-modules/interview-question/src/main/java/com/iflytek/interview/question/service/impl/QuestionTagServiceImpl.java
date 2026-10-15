package com.iflytek.interview.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.question.dto.QuestionTagDTO;
import com.iflytek.interview.question.entity.QuestionTag;
import com.iflytek.interview.question.mapper.QuestionTagMapper;
import com.iflytek.interview.question.service.QuestionCacheService;
import com.iflytek.interview.question.service.QuestionTagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class QuestionTagServiceImpl
        extends ServiceImpl<QuestionTagMapper, QuestionTag>
        implements QuestionTagService {

    private final QuestionCacheService questionCacheService;

    public QuestionTagServiceImpl(QuestionCacheService questionCacheService) {
        this.questionCacheService = questionCacheService;
    }

    @Override
    public List<QuestionTag> listTags(String type) {
        return lambdaQuery()
                .eq(type != null && !type.isBlank(), QuestionTag::getType, type)
                .eq(QuestionTag::getStatus, 1)
                .orderByAsc(QuestionTag::getType)
                .orderByAsc(QuestionTag::getSortOrder)
                .list();
    }

    @Override
    @Transactional
    public QuestionTag createTag(QuestionTagDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()
                || dto.getCode() == null || dto.getCode().isBlank()
                || dto.getType() == null || dto.getType().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        long exists = lambdaQuery().eq(QuestionTag::getCode, dto.getCode()).count();
        if (exists > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        QuestionTag tag = new QuestionTag();
        BeanUtils.copyProperties(dto, tag);
        tag.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        save(tag);
        log.info("新增标签: id={}, code={}, type={}", tag.getId(), tag.getCode(), tag.getType());
        return tag;
    }

    @Override
    @Transactional
    public void updateTag(Long id, QuestionTagDTO dto) {
        QuestionTag tag = getById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        BeanUtils.copyProperties(dto, tag);
        updateById(tag);
        questionCacheService.clearTagCache(id);
    }

    @Override
    @Transactional
    public void disableTag(Long id) {
        QuestionTag tag = getById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        tag.setStatus(0);
        updateById(tag);
        questionCacheService.clearTagCache(id);
    }
}