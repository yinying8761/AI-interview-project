package com.iflytek.interview.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Only seed a new dedicated demo database. Never reset an existing database. */
@Configuration
@Profile("demo")
public class DemoSchemaInitializer {
    @Bean
    public DataSourceInitializer demoSchema(DataSource dataSource) throws Exception {
        boolean exists;
        try (var connection = dataSource.getConnection();
             var tables = connection.getMetaData().getTables(null, null, "sys_user", null)) {
            exists = tables.next();
        }
        var init = new DataSourceInitializer();
        init.setDataSource(dataSource);
        init.setEnabled(!exists);
        var populator = new ResourceDatabasePopulator(new ClassPathResource("schema-demo.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        init.setDatabasePopulator(populator);
        return init;
    }
}
