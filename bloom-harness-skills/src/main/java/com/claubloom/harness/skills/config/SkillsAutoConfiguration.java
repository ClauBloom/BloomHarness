package com.claubloom.harness.skills.config;

import com.claubloom.harness.core.prompt.SystemPromptBuilder;
import com.claubloom.harness.core.tool.ToolRegistry;
import com.claubloom.harness.skills.model.Skill;
import com.claubloom.harness.skills.parser.SkillFrontmatterParser;
import com.claubloom.harness.skills.prompt.SkillPromptInjector;
import com.claubloom.harness.skills.scanner.SkillScanner;
import com.claubloom.harness.skills.tool.SkillTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * bloom-harness-skills 模块的 Spring Boot 自动装配类。
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(SkillsProperties.class)
public class SkillsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SkillFrontmatterParser skillFrontmatterParser() {
        return new SkillFrontmatterParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillScanner skillScanner() {
        return new SkillScanner();
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillPromptInjector skillPromptInjector() {
        return new SkillPromptInjector();
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillTool skillTool() {
        return new SkillTool();
    }

    @Bean
    public Object skillsInitializer(
            SkillsProperties properties,
            SkillScanner scanner,
            SkillPromptInjector injector,
            SkillTool skillTool,
            ToolRegistry toolRegistry
    ) {
        if (!properties.isEnabled()) {
            return new Object();
        }

        Path projectDir = Paths.get(properties.getProjectSkillsDir());
        Path userDir = Paths.get(properties.getUserSkillsDir());
        List<Path> extraDirs = new ArrayList<>();
        if (properties.getExtraDirs() != null) {
            for (String dir : properties.getExtraDirs()) {
                extraDirs.add(Paths.get(dir));
            }
        }

        Map<String, Skill> discoveredSkills = scanner.scan(projectDir, userDir, extraDirs);
        skillTool.registerSkills(discoveredSkills);

        if (toolRegistry != null) {
            toolRegistry.register(skillTool);
        }

        log.info("Initialized BloomHarness Skills: loaded {} skills", discoveredSkills.size());
        return new Object();
    }
}
