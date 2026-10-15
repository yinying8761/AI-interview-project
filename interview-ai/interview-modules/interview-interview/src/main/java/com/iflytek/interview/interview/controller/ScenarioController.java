package com.iflytek.interview.interview.controller;

import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.interview.dto.ScenarioDTO;
import com.iflytek.interview.interview.entity.Scenario;
import com.iflytek.interview.interview.service.ScenarioService;
import com.iflytek.interview.interview.vo.ScenarioVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    @Autowired
    private ScenarioService scenarioService;

    @GetMapping
    public Result<List<Scenario>> list(@RequestParam(required = false) String techField) {
        if (techField != null && !techField.isEmpty()) {
            return Result.success(scenarioService.listByField(techField));
        }
        return Result.success(scenarioService.listScenario());
    }

    @GetMapping("/{id}")
    public Result<Scenario> get(@PathVariable Long id) {
        return Result.success(scenarioService.getScenario(id));
    }

    @PreAuthorize("hasAuthority('scenario:create')")
    @PostMapping
    public Result<ScenarioVO> create(@RequestBody ScenarioDTO dto) {
        Scenario scenario = scenarioService.create(dto);
        ScenarioVO vo = new ScenarioVO();
        BeanUtils.copyProperties(scenario, vo);
        return Result.success(vo);
    }

    @PreAuthorize("hasAuthority('scenario:update')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ScenarioDTO dto) {
        scenarioService.updateScenario(id, dto);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('scenario:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scenarioService.deleteScenario(id);
        return Result.success();
    }
}
