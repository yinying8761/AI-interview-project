package com.iflytek.interview.interview.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iflytek.interview.interview.dto.ScenarioDTO;
import com.iflytek.interview.interview.entity.Scenario;

import java.util.List;

public interface ScenarioService extends IService<Scenario> {

    List<Scenario> listScenario();

    List<Scenario> listByField(String field);

    Scenario getScenario(Long id);

    Scenario create(ScenarioDTO dto);

    void updateScenario(Long id, ScenarioDTO dto);

    void deleteScenario(Long id);
}
