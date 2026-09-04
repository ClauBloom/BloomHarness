package com.claubloom.harness.extension;

import com.claubloom.harness.core.loop.AgentContext;
import com.claubloom.harness.core.tool.ToolContext;
import com.claubloom.harness.core.tool.ToolDefinition;
import com.claubloom.harness.core.tool.ToolRegistry;
import com.claubloom.harness.extension.hook.ExtensionHook;
import com.claubloom.harness.extension.manager.ExtensionManager;
import com.claubloom.harness.extension.model.Extension;
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.message.UserMessage;
import com.claubloom.harness.protocol.session.Usage;
import com.claubloom.harness.protocol.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 Smoke Tests for Extension Subsystem (TC-P3-03 & TC-P3-04).
 * Directly mirrors pi's extensions-runner.test.ts and extension types.
 */
public class ExtensionSmokeTest {

    private ToolRegistry toolRegistry;
    private ExtensionManager extensionManager;
    private List<String> eventTrace;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        extensionManager = new ExtensionManager(toolRegistry);
        eventTrace = new ArrayList<>();
    }

    /**
     * TC-P3-03: Extension Lifecycle Hook Execution and Tracing.
     */
    @Test
    @DisplayName("TC-P3-03: Extension lifecycle hooks should fire in correct sequence")
    void should_fireExtensionLifecycleHooksInSequence() {
        // Arrange - Register an auditing extension
        Extension auditExtension = Extension.builder()
                .id("audit-extension")
                .name("Audit Logging Plugin")
                .version("1.0.0")
                .description("Logs turn and tool lifecycle events")
                .hook(new ExtensionHook() {
                    @Override
                    public void onSessionStart(String sessionId) {
                        eventTrace.add("sessionStart:" + sessionId);
                    }

                    @Override
                    public void onSessionEnd(String sessionId) {
                        eventTrace.add("sessionEnd:" + sessionId);
                    }

                    @Override
                    public void beforeTurn(AgentContext context) {
                        eventTrace.add("beforeTurn:sess=" + context.getSessionId());
                    }

                    @Override
                    public void afterTurn(AgentContext context, AssistantMessage assistantMessage) {
                        eventTrace.add("afterTurn:msg=" + assistantMessage.id());
                    }

                    @Override
                    public void beforeToolCall(String toolName, Map<String, Object> arguments) {
                        eventTrace.add("beforeToolCall:" + toolName + ":" + arguments.get("key"));
                    }

                    @Override
                    public void afterToolCall(String toolName, Map<String, Object> arguments, ToolResult toolResult) {
                        eventTrace.add("afterToolCall:" + toolName + ":res=" + toolResult.output());
                    }
                })
                .build();

        extensionManager.registerExtension(auditExtension);

        // Act - Simulate Agent Session Flow
        String sessionId = "sess-100";
        AgentContext context = new AgentContext();
        context.setSessionId(sessionId);
        context.getMessages().add(UserMessage.text("Hello"));

        extensionManager.fireSessionStart(sessionId);
        extensionManager.fireBeforeTurn(context);

        extensionManager.fireBeforeToolCall("custom-tool", Map.of("key", "test-arg"));
        extensionManager.fireAfterToolCall("custom-tool", Map.of("key", "test-arg"), ToolResult.success("tool-ok"));

        AssistantMessage response = new AssistantMessage(
                "msg-1",
                "assistant",
                List.of(new TextContent("Done")),
                com.claubloom.harness.protocol.model.ModelRef.of("openai", "gpt-4o"),
                "gpt-4o",
                Usage.zero(),
                System.currentTimeMillis(),
                "complete",
                "stop",
                null
        );
        extensionManager.fireAfterTurn(context, response);
        extensionManager.fireSessionEnd(sessionId);

        // Assert - Verify strict chronological trace
        assertThat(eventTrace).containsExactly(
                "sessionStart:sess-100",
                "beforeTurn:sess=sess-100",
                "beforeToolCall:custom-tool:test-arg",
                "afterToolCall:custom-tool:res=tool-ok",
                "afterTurn:msg=msg-1",
                "sessionEnd:sess-100"
        );
    }

    /**
     * TC-P3-04: Dynamic Tool Registration via Extension.
     */
    @Test
    @DisplayName("TC-P3-04: Extensions should dynamically register custom tools into ToolRegistry")
    void should_dynamicallyRegisterCustomTools() throws Exception {
        // Arrange - Custom calculator tool contributed by extension
        ToolDefinition calcTool = new ToolDefinition() {
            @Override
            public String name() {
                return "calculator";
            }

            @Override
            public String description() {
                return "Calculate simple mathematical expressions";
            }

            @Override
            public Map<String, Object> parameterSchema() {
                return Map.of(
                        "type", "object",
                        "properties", Map.of("expr", Map.of("type", "string")),
                        "required", List.of("expr")
                );
            }

            @Override
            public CompletableFuture<ToolResult> execute(ToolContext context, Map<String, Object> arguments) {
                String expr = (String) arguments.get("expr");
                return CompletableFuture.completedFuture(ToolResult.success("result for " + expr + " = 42"));
            }
        };

        Extension mathPlugin = Extension.builder()
                .id("math-plugin")
                .name("Mathematics Extension")
                .version("1.2.0")
                .description("Provides math calculations")
                .tools(List.of(calcTool))
                .build();

        // Act - Register extension
        extensionManager.registerExtension(mathPlugin);

        // Assert - Tool is available in ToolRegistry
        assertThat(toolRegistry.contains("calculator")).isTrue();
        ToolDefinition retrievedTool = toolRegistry.find("calculator").orElse(null);
        assertThat(retrievedTool).isNotNull();
        assertThat(retrievedTool.description()).isEqualTo("Calculate simple mathematical expressions");

        // Act - Execute retrieved tool
        ToolContext ctx = new ToolContext("sess-1", ".", null, null);
        ToolResult result = retrievedTool.execute(ctx, Map.of("expr", "21*2")).get();
        assertThat(result.isError()).isFalse();
        assertThat(result.output()).isEqualTo("result for 21*2 = 42");
    }
}
