package com.claubloom.harness.skills.parser;

import com.claubloom.harness.skills.model.Skill;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

/**
 * Parses markdown frontmatter and body for Agent Skills.
 * Directly mirrors pi's frontmatter.ts and skills.ts loadSkillFromFile.
 */
public class SkillFrontmatterParser {

    private final Yaml yaml = new Yaml();

    /**
     * 将文件的原始文本内容解析为结构化的 Skill 技能记录。
     *
     * @param rawContent complete markdown text with optional YAML frontmatter
     * @param file target file path
     * @param scope "project" | "user" | "custom"
     * @return parsed and validated Skill
     */
    public Skill parse(String rawContent, Path file, String scope) {
        String name = null;
        String description = null;
        boolean disableModelInvocation = false;
        String body = rawContent != null ? rawContent : "";

        if (rawContent != null && rawContent.startsWith("---")) {
            int secondFence = rawContent.indexOf("---", 3);
            if (secondFence != -1) {
                String yamlSection = rawContent.substring(3, secondFence).trim();
                body = rawContent.substring(secondFence + 3).trim();

                try {
                    Map<String, Object> data = yaml.load(yamlSection);
                    if (data != null) {
                        if (data.get("name") instanceof String s) {
                            name = s.trim();
                        }
                        if (data.get("description") instanceof String s) {
                            description = s.trim();
                        }
                        if (Boolean.TRUE.equals(data.get("disable-model-invocation")) ||
                            Boolean.TRUE.equals(data.get("disableModelInvocation"))) {
                            disableModelInvocation = true;
                        }
                    }
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to parse YAML frontmatter in " + file + ": " + e.getMessage(), e);
                }
            }
        }

        // If name was not specified in frontmatter, fall back to directory name or base file name
        if (name == null || name.isBlank()) {
            Path parent = file.getParent();
            if (parent != null && !parent.getFileName().toString().equals("skills")) {
                name = parent.getFileName().toString();
            } else {
                String fileName = file.getFileName().toString();
                name = fileName.endsWith(".md") ? fileName.substring(0, fileName.length() - 3) : fileName;
            }
            // Normalize fallback name to valid skill name
            name = name.toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
            if (name.startsWith("-")) name = name.substring(1);
            if (name.endsWith("-")) name = name.substring(0, name.length() - 1);
        }

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Skill description is required in frontmatter: " + file);
        }

        Path absoluteFile = file.toAbsolutePath().normalize();
        Path baseDir = absoluteFile.getParent() != null ? absoluteFile.getParent() : absoluteFile;

        return new Skill(
                name,
                description,
                absoluteFile.toString(),
                baseDir.toString(),
                body,
                scope != null ? scope : "custom",
                disableModelInvocation
        );
    }
}
