package com.claubloom.harness.skills;

import com.claubloom.harness.core.tool.ToolContext;
import com.claubloom.harness.protocol.tool.ToolResult;
import com.claubloom.harness.skills.model.Skill;
import com.claubloom.harness.skills.parser.SkillFrontmatterParser;
import com.claubloom.harness.skills.prompt.SkillPromptInjector;
import com.claubloom.harness.skills.scanner.SkillScanner;
import com.claubloom.harness.skills.tool.SkillTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 阶段 3：Agent Skills 冒烟测试（TC-P3-01 与 TC-P3-02）。
 * 与 pi 的 skills.test.ts 及 Agent Skills 标准保持一致。
 */
public class SkillsSmokeTest {

    @TempDir
    Path tempDir;

    private SkillFrontmatterParser parser;
    private SkillScanner scanner;
    private SkillPromptInjector injector;
    private SkillTool skillTool;

    @BeforeEach
    void setUp() {
        parser = new SkillFrontmatterParser();
        scanner = new SkillScanner();
        injector = new SkillPromptInjector();
        skillTool = new SkillTool();
    }

    /**
     * TC-P3-01：技能元信息头解析与校验（Agent Skills 规范）。
     */
    @Test
    @DisplayName("TC-P3-01: Should parse YAML frontmatter, extract body, and validate name/description limits")
    void should_parseSkillFrontmatterAndValidateConstraints() throws Exception {
        Path skillFile = tempDir.resolve("valid-skill/SKILL.md");
        Files.createDirectories(skillFile.getParent());

        String validSkillContent = """
                ---
                name: code-review
                description: Review pull requests for code quality and security vulnerabilities
                disable-model-invocation: false
                ---
                # Code Review Skill
                Follow standard clean code guidelines.
                Ensure test coverage is above 80%.
                """;
        Files.writeString(skillFile, validSkillContent);

        // 解析合法技能
        Skill skill = parser.parse(validSkillContent, skillFile, "project");
        assertThat(skill).isNotNull();
        assertThat(skill.name()).isEqualTo("code-review");
        assertThat(skill.description()).isEqualTo("Review pull requests for code quality and security vulnerabilities");
        assertThat(skill.disableModelInvocation()).isFalse();
        assertThat(skill.content()).contains("# Code Review Skill");
        assertThat(skill.content()).contains("Ensure test coverage is above 80%.");

        // 校验非法名称（包含大写字母）
        assertThatThrownBy(() -> Skill.validateName("Invalid_Name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lowercase");

        // 校验非法名称（以连字符开头）
        assertThatThrownBy(() -> Skill.validateName("-bad-name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start or end with a hyphen");

        // 校验非法名称（包含连续连字符）
        assertThatThrownBy(() -> Skill.validateName("bad--name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consecutive hyphens");

        // 校验非法描述（为空）
        assertThatThrownBy(() -> Skill.validateDescription(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be empty");
    }

    /**
     * TC-P3-02：多目录发现、冲突优先级与 XML 系统提示词注入。
     */
    @Test
    @DisplayName("TC-P3-02: Project skills should override user skills, format into standard XML prompt, and execute via tool")
    void should_resolveSkillPrecedenceAndInjectPrompt() throws Exception {
        Path projectSkillsDir = tempDir.resolve("workspace/.bloom/skills");
        Path userSkillsDir = tempDir.resolve("home/.bloom/skills");
        Files.createDirectories(projectSkillsDir);
        Files.createDirectories(userSkillsDir);

        // 1. 用户全局技能 "deploy"
        Path userDeploy = userSkillsDir.resolve("deploy/SKILL.md");
        Files.createDirectories(userDeploy.getParent());
        Files.writeString(userDeploy, """
                ---
                name: deploy
                description: Global deploy skill
                ---
                Deploy to staging server.
                """);

        // 2. 项目级技能 "deploy"（应覆盖用户技能）
        Path projectDeploy = projectSkillsDir.resolve("deploy/SKILL.md");
        Files.createDirectories(projectDeploy.getParent());
        Files.writeString(projectDeploy, """
                ---
                name: deploy
                description: Project customized deploy skill
                ---
                Deploy to production kubernetes cluster with canary.
                """);

        // 3. 用户全局技能 "lint"（与项目无冲突）
        Path userLint = userSkillsDir.resolve("lint/SKILL.md");
        Files.createDirectories(userLint.getParent());
        Files.writeString(userLint, """
                ---
                name: lint
                description: Run linter checks
                ---
                Run checkstyle and spotbugs.
                """);

        // 4. 隐藏技能（disable-model-invocation: true）
        Path secretSkill = projectSkillsDir.resolve("secret/SKILL.md");
        Files.createDirectories(secretSkill.getParent());
        Files.writeString(secretSkill, """
                ---
                name: internal-debug
                description: Internal debugging commands
                disable-model-invocation: true
                ---
                Sensitive diagnostic instructions.
                """);

        // 扫描技能
        Map<String, Skill> skills = scanner.scan(projectSkillsDir, userSkillsDir, List.of());
        assertThat(skills).hasSize(3);

        // 校验冲突优先级：项目 deploy 覆盖用户 deploy
        Skill deploySkill = skills.get("deploy");
        assertThat(deploySkill).isNotNull();
        assertThat(deploySkill.scope()).isEqualTo("project");
        assertThat(deploySkill.description()).isEqualTo("Project customized deploy skill");
        assertThat(deploySkill.content()).contains("Deploy to production kubernetes cluster with canary.");

        // 校验用户 lint 已被收录
        assertThat(skills.get("lint")).isNotNull();

        // 校验按 Agent Skills 标准进行 XML 提示词注入
        String xmlPrompt = injector.formatSkillsForPrompt(skills.values());
        assertThat(xmlPrompt).contains("<available_skills>");
        assertThat(xmlPrompt).contains("<name>deploy</name>");
        assertThat(xmlPrompt).contains("<description>Project customized deploy skill</description>");
        assertThat(xmlPrompt).contains("<name>lint</name>");
        // disable-model-invocation=true 的隐藏技能必须从提示词中排除
        assertThat(xmlPrompt).doesNotContain("<name>internal-debug</name>");

        // 测试 SkillTool 执行
        skillTool.registerSkills(skills);
        ToolContext ctx = new ToolContext("s1", tempDir.toString(), null, null);
        ToolResult result = skillTool.execute(ctx, Map.of("name", "deploy")).get();
        assertThat(result.isError()).isFalse();
        assertThat(result.output()).contains("Skill: deploy");
        assertThat(result.output()).contains("Deploy to production kubernetes cluster with canary.");
    }
}
