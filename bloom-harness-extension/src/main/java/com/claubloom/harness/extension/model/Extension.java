package com.claubloom.harness.extension.model;

import com.claubloom.harness.core.tool.ToolDefinition;
import com.claubloom.harness.extension.hook.ExtensionHook;
import lombok.Builder;

import java.util.List;
import java.util.Objects;

/**
 * Represents a registered extension plugin.
 */
@Builder
public record Extension(
        String id,
        String name,
        String version,
        String description,
        ExtensionHook hook,
        List<ToolDefinition> tools
) {
    public Extension {
        Objects.requireNonNull(id, "Extension id cannot be null");
        Objects.requireNonNull(name, "Extension name cannot be null");
        tools = tools != null ? List.copyOf(tools) : List.of();
    }
}
