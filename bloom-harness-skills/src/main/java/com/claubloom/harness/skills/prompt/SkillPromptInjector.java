package com.claubloom.harness.skills.prompt;

import com.claubloom.harness.skills.model.Skill;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Formats visible skills for system prompt injection in Agent Skills standard XML format.
 * Directly mirrors pi's formatSkillsForPrompt in skills.ts.
 */
@Component
public class SkillPromptInjector {

    /**
     * Format visible skills into XML block per Agent Skills standard.
     */
    public String formatSkillsForPrompt(Collection<Skill> skills) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }

        List<Skill> visibleSkills = skills.stream()
                .filter(s -> !s.disableModelInvocation())
                .toList();

        if (visibleSkills.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\nThe following skills provide specialized instructions for specific tasks.\n");
        sb.append("Use the read tool to load a skill's file when the task matches its description.\n");
        sb.append("When a skill file references a relative path, resolve it against the skill directory (parent of SKILL.md / dirname of the path) and use that absolute path in tool commands.\n\n");
        sb.append("<available_skills>\n");

        for (Skill skill : visibleSkills) {
            sb.append("  <skill>\n");
            sb.append("    <name>").append(escapeXml(skill.name())).append("</name>\n");
            sb.append("    <description>").append(escapeXml(skill.description())).append("</description>\n");
            sb.append("    <location>").append(escapeXml(skill.filePath())).append("</location>\n");
            sb.append("  </skill>\n");
        }

        sb.append("</available_skills>");
        return sb.toString();
    }

    /**
     * Injects the formatted skills XML into an existing system prompt.
     */
    public String injectIntoSystemPrompt(String systemPrompt, Collection<Skill> skills) {
        String skillsXml = formatSkillsForPrompt(skills);
        if (skillsXml.isEmpty()) {
            return systemPrompt != null ? systemPrompt : "";
        }
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return skillsXml.trim();
        }
        return systemPrompt.trim() + skillsXml;
    }

    private String escapeXml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
