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
     * Whether skill scanning and injection is enabled.
     */
    private boolean enabled = true;

    /**
     * Project skills directory relative to workspace root (e.g. ".bloom/skills" or "skills").
     */
    private String projectSkillsDir = ".bloom/skills";

    /**
     * Global user skills directory (e.g. "~/.bloom/skills").
     */
    private String userSkillsDir = System.getProperty("user.home") + "/.bloom/skills";

    /**
     * Extra custom skill directories to scan.
     */
    private List<String> extraDirs = new ArrayList<>();
}
