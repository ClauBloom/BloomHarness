package com.claubloom.harness.core.harness;

import com.claubloom.harness.core.tool.ToolDefinition;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 当前活动 Harness 实例所聚合的全部工程资源（工具集、技能库、提示词模板）。
 */
public record HarnessResources(
        @JsonProperty(value = "tools", required = true)
        List<ToolDefinition> tools,
        @JsonProperty("systemPromptTemplate")
        String systemPromptTemplate
) {
    public static HarnessResources of(List<ToolDefinition> tools, String systemPromptTemplate) {
        return new HarnessResources(tools, systemPromptTemplate);
    }
}
