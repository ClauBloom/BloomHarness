package com.claubloom.harness.storage.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.claubloom.harness.storage.initializer.SqliteDatabaseInitializer;
import com.claubloom.harness.storage.mapper.SessionEntryMapper;
import com.claubloom.harness.storage.mapper.SessionMapper;
import com.claubloom.harness.storage.mapper.SessionStatsMapper;
import com.claubloom.harness.storage.service.SessionStorageService;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * bloom-harness-storage 模块的 Spring Boot 自动装配类。
 */
@Slf4j
@AutoConfiguration
@AutoConfigureAfter(MybatisPlusAutoConfiguration.class)
@EnableConfigurationProperties(StorageProperties.class)
@MapperScan("com.claubloom.harness.storage.mapper")
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SqliteDatabaseInitializer sqliteDatabaseInitializer(DataSource dataSource) {
        return new SqliteDatabaseInitializer(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionStorageService sessionStorageService(
            SessionMapper sessionMapper,
            SessionEntryMapper sessionEntryMapper,
            SessionStatsMapper sessionStatsMapper
    ) {
        return new SessionStorageService(sessionMapper, sessionEntryMapper, sessionStatsMapper);
    }

    @Bean
    public Object databaseInitializerRunner(
            StorageProperties properties,
            SqliteDatabaseInitializer initializer
    ) {
        if (properties.isAutoInitialize()) {
            initializer.initialize();
        }
        return new Object();
    }
}
