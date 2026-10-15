package com.iflytek.interview.interview.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.interview.dto.ScenarioDTO;
import com.iflytek.interview.interview.entity.Scenario;
import com.iflytek.interview.interview.mapper.ScenarioMapper;
import com.iflytek.interview.interview.service.ScenarioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class ScenarioServiceImpl
        extends ServiceImpl<ScenarioMapper, Scenario>
        implements ScenarioService {

    @Override
    public List<Scenario> listScenario() {
        log.info("查数据库：场景列表（含实时题目数量）");
        return baseMapper.selectWithQuestionCount(null, SecurityUtil.hasRole("admin"));
    }

    @Override
    public Scenario getScenario(Long id) {
        Scenario scenario = getById(id);
        if (scenario == null) {
            throw new BusinessException(ErrorCode.SCENARIO_NOT_FOUND);
        }
        if (scenario.getStatus() != null
                && scenario.getStatus() == 0
                && !SecurityUtil.hasRole("admin")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return scenario;
    }

    @Override
    public List<Scenario> listByField(String field) {
        log.info("查数据库：场景列表 field={}（含实时题目数量）", field);
        return baseMapper.selectWithQuestionCount(field, false);
    }

    @Override
    @Transactional
    public Scenario create(ScenarioDTO dto) {
        Scenario scenario = new Scenario();
        BeanUtils.copyProperties(dto, scenario);
        scenario.setStatus(1);
        save(scenario);
        return scenario;
    }

    @Override
    @Transactional
    public void updateScenario(Long id, ScenarioDTO dto) {
        Scenario scenario = getById(id);
        if (scenario == null) {
            throw new BusinessException(ErrorCode.SCENARIO_NOT_FOUND);
        }
        BeanUtils.copyProperties(dto, scenario);
        updateById(scenario);
    }

    @Override
    @Transactional
    public void deleteScenario(Long id) {
        Scenario scenario = getById(id);
        if (scenario == null) {
            throw new BusinessException(ErrorCode.SCENARIO_NOT_FOUND);
        }
        removeById(id);
    }
}
