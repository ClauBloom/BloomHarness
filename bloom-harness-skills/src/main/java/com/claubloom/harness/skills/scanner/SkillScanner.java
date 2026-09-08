package com.claubloom.harness.skills.scanner;

import com.claubloom.harness.skills.model.Skill;
import com.claubloom.harness.skills.parser.SkillFrontmatterParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * 递归扫描目录以发现 Agent Skills（SKILL.md 与 *.md）。
 * 按 user → custom → project 的优先级加载，同名时高优先级的技能覆盖低优先级的技能。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillScanner {

    private final SkillFrontmatterParser parser = new SkillFrontmatterParser();
    private static final Set<String> IGNORED_DIRS = Set.of(".git", "node_modules", "target", ".m2-repo", ".idea", ".vscode");

    /**
     * 按优先级扫描多个目录：
     * 同名时项目技能覆盖用户/全局技能。
     *
     * @param projectDir 工作区技能目录
     * @param userDir 全局用户技能目录
     * @param extraDirs 额外的自定义目录
     * @return 技能名称到 Skill 的映射
     */
    public Map<String, Skill> scan(Path projectDir, Path userDir, List<Path> extraDirs) {
        Map<String, Skill> skillMap = new LinkedHashMap<>();

        // 1. 先加载用户/全局技能（优先级最低）
        if (userDir != null && Files.exists(userDir)) {
            loadFromDirectory(userDir, "user", skillMap);
        }

        // 2. 再加载额外自定义技能（优先级中等）
        if (extraDirs != null) {
            for (Path dir : extraDirs) {
                if (dir != null && Files.exists(dir)) {
                    loadFromDirectory(dir, "custom", skillMap);
                }
            }
        }

        // 3. 最后加载项目技能（优先级最高——覆盖用户与自定义技能）
        if (projectDir != null && Files.exists(projectDir)) {
            loadFromDirectory(projectDir, "project", skillMap);
        }

        return skillMap;
    }

    public void loadFromDirectory(Path baseDir, String scope, Map<String, Skill> targetMap) {
        try {
            Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (IGNORED_DIRS.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    // 若该目录包含 SKILL.md，则加载它并跳过更深的子树
                    Path skillMd = dir.resolve("SKILL.md");
                    if (Files.exists(skillMd) && Files.isRegularFile(skillMd)) {
                        tryLoadSkill(skillMd, scope, targetMap);
                        if (!dir.equals(baseDir)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }

                    String fileName = file.getFileName().toString();
                    if (fileName.equals("SKILL.md") || (fileName.endsWith(".md") && !fileName.startsWith("."))) {
                        tryLoadSkill(file, scope, targetMap);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Failed scanning skills directory {}: {}", baseDir, e.getMessage());
        }
    }

    private void tryLoadSkill(Path file, String scope, Map<String, Skill> targetMap) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Skill skill = parser.parse(content, file, scope);
            // 优先级更高的技能替换已有条目
            targetMap.put(skill.name(), skill);
            log.debug("Loaded skill '{}' from {} (scope={})", skill.name(), file, scope);
        } catch (Exception e) {
            log.warn("Skipping invalid skill file {}: {}", file, e.getMessage());
        }
    }
}
