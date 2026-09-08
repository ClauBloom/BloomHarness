package com.claubloom.harness.skills.parser;

import com.claubloom.harness.skills.model.Skill;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

/**
 * 解析 Skill Markdown 文件的 YAML 元信息头（frontmatter）与正文内容。
 * 支持从元信息头提取名称、描述及 disable-model-invocation 标志；
 * 若未指定名称，则回退使用目录名或基础文件名并规范化。
 */
public class SkillFrontmatterParser {

    private final Yaml yaml = new Yaml();

    /**
     * 将文件的原始文本内容解析为结构化的 Skill 技能记录。
     *
     * @param rawContent 完整的 Markdown 文本，可含可选的 YAML 元信息头
     * @param file 目标文件路径
     * @param scope 作用域："project" | "user" | "custom"
     * @return 解析并校验后的 Skill
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

        // 若元信息头中未指定名称，则回退使用目录名或基础文件名
        if (name == null || name.isBlank()) {
            Path parent = file.getParent();
            if (parent != null && !parent.getFileName().toString().equals("skills")) {
                name = parent.getFileName().toString();
            } else {
                String fileName = file.getFileName().toString();
                name = fileName.endsWith(".md") ? fileName.substring(0, fileName.length() - 3) : fileName;
            }
            // 将回退得到的名称规范化为合法的技能名称
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
