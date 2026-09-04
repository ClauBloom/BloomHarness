package com.claubloom.harness.skills.tool;

import com.claubloom.harness.core.tool.ToolContext;
import com.claubloom.harness.core.tool.ToolDefinition;
import com.claubloom.harness.protocol.tool.ToolResult;
import com.claubloom.harness.skills.model.Skill;
import com.claubloom.harness.skills.scanner.SkillScanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Built-in Skill tool allowing the Agent to dynamically load instructions for a skill by name.
 * Directly mirrors pi's skill invocation mechanics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillTool implements ToolDefinition {

    private final Map<String, Skill> registeredSkills = new ConcurrentHashMap<>();

    public void registerSkills(Map<String, Skill> skills) {
        if (skills != null) {
            registeredSkills.putAll(skills);
        }
    }

    public void registerSkill(Skill skill) {
        if (skill != null) {
            registeredSkills.put(skill.name(), skill);
        }
    }

    public Skill getSkill(String name) {
        return name != null ? registeredSkills.get(name) : null;
    }

    public Map<String, Skill> getAllSkills() {
        return Map.copyOf(registeredSkills);
    }

    @Override
    public String name() {
        return "skill";
    }

    @Override
    public String description() {
        return "Load the full instructions for an available skill. Call this with the exact skill name when a task matches that skill.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "The exact name of the skill to load")
                ),
                "required", List.of("name")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(ToolContext context, Map<String, Object> arguments) {
        return CompletableFuture.supplyAsync(() -> {
            String skillName = (String) arguments.get("name");
            if (skillName == null || skillName.isBlank()) {
                return ToolResult.error("Argument 'name' is required");
            }

            Skill skill = registeredSkills.get(skillName.trim());
            if (skill == null) {
                return ToolResult.error(String.format("Skill '%s' not found. Available skills: %s",
                        skillName, registeredSkills.keySet()));
            }

            StringBuilder output = new StringBuilder();
            output.append(String.format("Skill: %s\n", skill.name()));
            output.append(String.format("Description: %s\n", skill.description()));
            output.append(String.format("Location: %s\n", skill.filePath()));
            output.append(String.format("Base Directory: %s\n\n", skill.baseDir()));
            output.append(skill.content());

            return ToolResult.success(output.toString());
        });
    }
}
