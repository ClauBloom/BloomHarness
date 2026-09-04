package com.claubloom.harness.extension.manager;

import com.claubloom.harness.core.loop.AgentContext;
import com.claubloom.harness.core.tool.ToolDefinition;
import com.claubloom.harness.core.tool.ToolRegistry;
import com.claubloom.harness.extension.hook.ExtensionHook;
import com.claubloom.harness.extension.model.Extension;
import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Extension Manager managing installed extensions, dispatching lifecycle hooks,
 * and registering extension-contributed tools into ToolRegistry.
 * Directly mirrors pi's extension runner and lifecycle dispatching.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtensionManager {

    private final ToolRegistry toolRegistry;
    private final Map<String, Extension> extensions = new ConcurrentHashMap<>();
    private final List<ExtensionHook> hooks = new CopyOnWriteArrayList<>();

    /**
     * Register a new Extension plugin.
     * Automatically registers its hook and contributed tools.
     */
    public synchronized void registerExtension(Extension extension) {
        if (extension == null) return;

        extensions.put(extension.id(), extension);
        if (extension.hook() != null) {
            hooks.add(extension.hook());
        }

        if (toolRegistry != null && extension.tools() != null) {
            for (ToolDefinition tool : extension.tools()) {
                toolRegistry.register(tool);
                log.info("Extension '{}' registered custom tool '{}'", extension.id(), tool.name());
            }
        }

        log.info("Registered Extension '{}' (v{}, {})", extension.name(), extension.version(), extension.id());
    }

    public void registerHook(ExtensionHook hook) {
        if (hook != null) {
            hooks.add(hook);
        }
    }

    public Optional<Extension> getExtension(String id) {
        return Optional.ofNullable(extensions.get(id));
    }

    public Collection<Extension> getAllExtensions() {
        return Collections.unmodifiableCollection(extensions.values());
    }

    // Lifecycle hook dispatchers

    public void fireSessionStart(String sessionId) {
        for (ExtensionHook hook : hooks) {
            try {
                hook.onSessionStart(sessionId);
            } catch (Exception e) {
                log.warn("Error executing onSessionStart hook: {}", e.getMessage(), e);
            }
        }
    }

    public void fireSessionEnd(String sessionId) {
        for (ExtensionHook hook : hooks) {
            try {
                hook.onSessionEnd(sessionId);
            } catch (Exception e) {
                log.warn("Error executing onSessionEnd hook: {}", e.getMessage(), e);
            }
        }
    }

    public void fireBeforeTurn(AgentContext context) {
        for (ExtensionHook hook : hooks) {
            try {
                hook.beforeTurn(context);
            } catch (Exception e) {
                log.warn("Error executing beforeTurn hook: {}", e.getMessage(), e);
            }
        }
    }

    public void fireAfterTurn(AgentContext context, AssistantMessage assistantMessage) {
        for (ExtensionHook hook : hooks) {
            try {
                hook.afterTurn(context, assistantMessage);
            } catch (Exception e) {
                log.warn("Error executing afterTurn hook: {}", e.getMessage(), e);
            }
        }
    }

    public void fireBeforeToolCall(String toolName, Map<String, Object> arguments) {
        for (ExtensionHook hook : hooks) {
            try {
                hook.beforeToolCall(toolName, arguments);
            } catch (Exception e) {
                log.warn("Error executing beforeToolCall hook: {}", e.getMessage(), e);
            }
        }
    }

    public void fireAfterToolCall(String toolName, Map<String, Object> arguments, ToolResult toolResult) {
        for (ExtensionHook hook : hooks) {
            try {
                hook.afterToolCall(toolName, arguments, toolResult);
            } catch (Exception e) {
                log.warn("Error executing afterToolCall hook: {}", e.getMessage(), e);
            }
        }
    }
}
