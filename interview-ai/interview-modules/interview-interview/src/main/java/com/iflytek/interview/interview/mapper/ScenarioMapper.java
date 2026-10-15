package com.iflytek.interview.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.interview.interview.entity.Scenario;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ScenarioMapper extends BaseMapper<Scenario> {

    /**
     * 查询场景并实时统计启用题目数量，避免依赖可能未同步的 question_count 冗余字段。
     */
    @Select("""
            <script>
            SELECT
                s.id,
                s.name,
                s.description,
                s.tech_field,
                s.difficulty,
                s.cover_image,
                s.sort_order,
                s.status,
                s.created_at,
                s.updated_at,
                (
                    SELECT COUNT(*)
                    FROM interview_question q
                    WHERE q.scenario_id = s.id
                      AND q.status = 1
                ) AS question_count
            FROM interview_scenario s
            WHERE 1 = 1
            <if test="includeDisabled != true">
                AND s.status = 1
            </if>
            <if test="techField != null and techField != ''">
                AND s.tech_field = #{techField}
            </if>
            ORDER BY s.sort_order ASC
            </script>
            """)
    List<Scenario> selectWithQuestionCount(@Param("techField") String techField,
                                           @Param("includeDisabled") boolean includeDisabled);
}
