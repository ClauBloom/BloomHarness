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
 * Scans directories for Agent Skills (SKILL.md and *.md) and resolves collisions by scope.
 * Directly mirrors pi's skills.ts loadSkills and loadSkillsFromDir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillScanner {

    private final SkillFrontmatterParser parser = new SkillFrontmatterParser();
    private static final Set<String> IGNORED_DIRS = Set.of(".git", "node_modules", "target", ".m2-repo", ".idea", ".vscode");

    /**
     * Scan multiple directories with precedence:
     * project skills override user/global skills with the same name.
     *
     * @param projectDir workspace skills directory
     * @param userDir global user skills directory
     * @param extraDirs additional custom directories
     * @return map of skill name to Skill
     */
    public Map<String, Skill> scan(Path projectDir, Path userDir, List<Path> extraDirs) {
        Map<String, Skill> skillMap = new LinkedHashMap<>();

        // 1. Load user/global skills first (lowest precedence)
        if (userDir != null && Files.exists(userDir)) {
            loadFromDirectory(userDir, "user", skillMap);
        }

        // 2. Load extra custom skills (medium precedence)
        if (extraDirs != null) {
            for (Path dir : extraDirs) {
                if (dir != null && Files.exists(dir)) {
                    loadFromDirectory(dir, "custom", skillMap);
                }
            }
        }

        // 3. Load project skills (highest precedence - overrides user and custom)
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

                    // If this directory contains a SKILL.md, load it and skip deeper subtrees (mirrors pi's rule)
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
            // Higher precedence replaces existing entry
            targetMap.put(skill.name(), skill);
            log.debug("Loaded skill '{}' from {} (scope={})", skill.name(), file, scope);
        } catch (Exception e) {
            log.warn("Skipping invalid skill file {}: {}", file, e.getMessage());
        }
    }
}
