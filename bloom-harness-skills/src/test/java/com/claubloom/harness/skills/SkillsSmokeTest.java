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
 * Phase 3 Smoke Tests for Agent Skills (TC-P3-01 & TC-P3-02).
 * Directly mirrors pi's skills.test.ts and Agent Skills standard.
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
     * TC-P3-01: Skill Frontmatter Parsing and Validation (Agent Skills spec).
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

        // Parse valid skill
        Skill skill = parser.parse(validSkillContent, skillFile, "project");
        assertThat(skill).isNotNull();
        assertThat(skill.name()).isEqualTo("code-review");
        assertThat(skill.description()).isEqualTo("Review pull requests for code quality and security vulnerabilities");
        assertThat(skill.disableModelInvocation()).isFalse();
        assertThat(skill.content()).contains("# Code Review Skill");
        assertThat(skill.content()).contains("Ensure test coverage is above 80%.");

        // Validate invalid name (contains uppercase)
        assertThatThrownBy(() -> Skill.validateName("Invalid_Name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lowercase");

        // Validate invalid name (starts with hyphen)
        assertThatThrownBy(() -> Skill.validateName("-bad-name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start or end with a hyphen");

        // Validate invalid name (consecutive hyphens)
        assertThatThrownBy(() -> Skill.validateName("bad--name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consecutive hyphens");

        // Validate invalid description (empty)
        assertThatThrownBy(() -> Skill.validateDescription(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be empty");
    }

    /**
     * TC-P3-02: Multi-Directory Discovery, Collision Precedence, and XML System Prompt Injection.
     */
    @Test
    @DisplayName("TC-P3-02: Project skills should override user skills, format into standard XML prompt, and execute via tool")
    void should_resolveSkillPrecedenceAndInjectPrompt() throws Exception {
        Path projectSkillsDir = tempDir.resolve("workspace/.bloom/skills");
        Path userSkillsDir = tempDir.resolve("home/.bloom/skills");
        Files.createDirectories(projectSkillsDir);
        Files.createDirectories(userSkillsDir);

        // 1. User global skill "deploy"
        Path userDeploy = userSkillsDir.resolve("deploy/SKILL.md");
        Files.createDirectories(userDeploy.getParent());
        Files.writeString(userDeploy, """
                ---
                name: deploy
                description: Global deploy skill
                ---
                Deploy to staging server.
                """);

        // 2. Project-level skill "deploy" (should override user skill)
        Path projectDeploy = projectSkillsDir.resolve("deploy/SKILL.md");
        Files.createDirectories(projectDeploy.getParent());
        Files.writeString(projectDeploy, """
                ---
                name: deploy
                description: Project customized deploy skill
                ---
                Deploy to production kubernetes cluster with canary.
                """);

        // 3. User global skill "lint" (no project collision)
        Path userLint = userSkillsDir.resolve("lint/SKILL.md");
        Files.createDirectories(userLint.getParent());
        Files.writeString(userLint, """
                ---
                name: lint
                description: Run linter checks
                ---
                Run checkstyle and spotbugs.
                """);

        // 4. Hidden skill (disable-model-invocation: true)
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

        // Scan skills
        Map<String, Skill> skills = scanner.scan(projectSkillsDir, userSkillsDir, List.of());
        assertThat(skills).hasSize(3);

        // Verify collision precedence: project deploy overrides user deploy
        Skill deploySkill = skills.get("deploy");
        assertThat(deploySkill).isNotNull();
        assertThat(deploySkill.scope()).isEqualTo("project");
        assertThat(deploySkill.description()).isEqualTo("Project customized deploy skill");
        assertThat(deploySkill.content()).contains("Deploy to production kubernetes cluster with canary.");

        // Verify user lint is included
        assertThat(skills.get("lint")).isNotNull();

        // Verify XML prompt injection per Agent Skills standard
        String xmlPrompt = injector.formatSkillsForPrompt(skills.values());
        assertThat(xmlPrompt).contains("<available_skills>");
        assertThat(xmlPrompt).contains("<name>deploy</name>");
        assertThat(xmlPrompt).contains("<description>Project customized deploy skill</description>");
        assertThat(xmlPrompt).contains("<name>lint</name>");
        // Hidden skill with disable-model-invocation=true MUST be excluded from prompt
        assertThat(xmlPrompt).doesNotContain("<name>internal-debug</name>");

        // Test SkillTool execution
        skillTool.registerSkills(skills);
        ToolContext ctx = new ToolContext("s1", tempDir.toString(), null, null);
        ToolResult result = skillTool.execute(ctx, Map.of("name", "deploy")).get();
        assertThat(result.isError()).isFalse();
        assertThat(result.output()).contains("Skill: deploy");
        assertThat(result.output()).contains("Deploy to production kubernetes cluster with canary.");
    }
}
