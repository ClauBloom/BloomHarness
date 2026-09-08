package com.claubloom.harness.skills.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * bloom-harness-skills 模块的配置属性。
 */
@Data
@ConfigurationProperties(prefix = "bloom.skills")
public class SkillsProperties {

    /**
     * 是否启用技能扫描与注入。
     */
    private boolean enabled = true;

    /**
     * 项目技能目录（相对于工作区根目录，例如 ".bloom/skills" 或 "skills"）。
     */
    private String projectSkillsDir = ".bloom/skills";

    /**
     * 全局用户技能目录（例如 "~/.bloom/skills"）。
     */
    private String userSkillsDir = System.getProperty("user.home") + "/.bloom/skills";

    /**
     * 需要扫描的额外自定义技能目录。
     */
    private List<String> extraDirs = new ArrayList<>();
}
