package com.claubloom.harness.skills.prompt;

import com.claubloom.harness.skills.model.Skill;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 将可见技能格式化为 Agent Skills 标准 XML 块，供系统提示词注入使用。
 * 标记为 {@code disable-model-invocation} 的技能会被自动排除。
 */
@Component
public class SkillPromptInjector {

    /**
     * 按 Agent Skills 标准将可见技能格式化为 XML 块。
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
        sb.append("Call the 'skill' tool with the skill's exact name to load its full instructions when a task matches its description.\n");
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
     * 将格式化后的技能 XML 注入已有的系统提示词中。
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
