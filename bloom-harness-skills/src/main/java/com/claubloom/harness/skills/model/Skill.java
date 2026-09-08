package com.claubloom.harness.skills.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 表示一个包含元数据与指令的 Agent Skill。
 * 名称仅允许小写字母、数字与连字符，描述为必填项。
 */
public record Skill(
        String name,
        String description,
        String filePath,
        String baseDir,
        String content,
        String scope, // 作用域："project" | "user" | "custom"
        boolean disableModelInvocation
) {
    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_DESCRIPTION_LENGTH = 1024;
    public static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9-]+$");

    public Skill {
        Objects.requireNonNull(name, "Skill name cannot be null");
        Objects.requireNonNull(description, "Skill description cannot be null");
        Objects.requireNonNull(filePath, "Skill filePath cannot be null");
        validateName(name);
        validateDescription(description);
    }

    public static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Skill name cannot be empty");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Skill name exceeds " + MAX_NAME_LENGTH + " characters: " + name);
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Skill name must contain only lowercase a-z, 0-9, and hyphens: " + name);
        }
        if (name.startsWith("-") || name.endsWith("-")) {
            throw new IllegalArgumentException("Skill name must not start or end with a hyphen: " + name);
        }
        if (name.contains("--")) {
            throw new IllegalArgumentException("Skill name must not contain consecutive hyphens: " + name);
        }
    }

    public static void validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Skill description cannot be empty");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Skill description exceeds " + MAX_DESCRIPTION_LENGTH + " characters: " + description.length());
        }
    }
}
