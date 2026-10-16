package com.iflytek.interview.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * demo 库增量迁移（幂等）：把后续章节新增的表/列补到「已存在的 demo 库」。
 * schema-demo.sql 只在全新库首次执行；老 demo 库缺新列会导致 500，
 * 这里在每次启动时跑 schema-demo-delta.sql 补齐，且每句可重复执行。
 * 作为 ApplicationRunner，天然晚于 DataSourceInitializer（首次建库）运行。
 */
@Slf4j
@Component
@Profile("demo")
public class DemoDeltaInitializer implements ApplicationRunner {

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource("schema-demo-delta.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        try {
            DatabasePopulatorUtils.execute(populator, dataSource);
            log.info("[demo 增量迁移] 已完成 schema-demo-delta.sql");
        } catch (Exception e) {
            log.error("[demo 增量迁移] 执行失败，请检查 schema-demo-delta.sql", e);
        }
    }
}
