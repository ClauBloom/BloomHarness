package com.claubloom.harness.core.prompt;

import com.claubloom.harness.core.tool.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

/**
 * SystemPromptBuilder 按照分层架构规范组装系统提示词（基础准则、工作区、环境、工具与技能）。
 */
@Slf4j
@Component
public class SystemPromptBuilder {

    private static final String DEFAULT_BASE_PROMPT = """
            You are an expert AI coding assistant and software engineer.
            You help users review, write, edit, and debug code, as well as execute commands in their workspace.
            """;

    /**
     * 组装包含工具集、当前工作目录、运行环境与自定义准则的完整系统提示词。
     */
    public String buildSystemPrompt(
            String cwd,
            String customPrompt,
            Collection<ToolDefinition> tools,
            String skillsXml
    ) {
        StringBuilder sb = new StringBuilder();

        // 1. Base identity & instruction
        if (customPrompt != null && !customPrompt.isBlank()) {
            sb.append(customPrompt.trim()).append("\n\n");
        } else {
            sb.append(DEFAULT_BASE_PROMPT.trim()).append("\n\n");
        }

        // 2. Working environment information
        sb.append("## Environment\n");
        sb.append("- Current Date: ").append(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)).append("\n");
        if (cwd != null && !cwd.isBlank()) {
            sb.append("- Current Working Directory: `").append(cwd).append("`\n");
        }
        sb.append("- OS: ").append(System.getProperty("os.name")).append("\n\n");

        // 3. Available Tools section
        if (tools != null && !tools.isEmpty()) {
            sb.append("## Available Tools\n");
            for (ToolDefinition tool : tools) {
                sb.append("- `").append(tool.name()).append("`: ").append(tool.description()).append("\n");
            }
            sb.append("\n");
        }

        // 4. Injected Skills XML section
        if (skillsXml != null && !skillsXml.isBlank()) {
            sb.append(skillsXml.trim()).append("\n\n");
        }

        return sb.toString().trim();
    }
}
