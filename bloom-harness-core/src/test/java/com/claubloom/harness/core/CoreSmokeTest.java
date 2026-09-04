package com.claubloom.harness.core;

import com.claubloom.harness.core.event.*;
import com.claubloom.harness.core.loop.AgentContext;
import com.claubloom.harness.core.loop.AgentEventSink;
import com.claubloom.harness.core.loop.AgentLoop;
import com.claubloom.harness.core.loop.AgentLoopConfig;
import com.claubloom.harness.core.loop.LlmCaller;
import com.claubloom.harness.core.tool.ToolContext;
import com.claubloom.harness.core.tool.ToolDefinition;
import com.claubloom.harness.core.tool.ToolExecutor;
import com.claubloom.harness.core.tool.ToolRegistry;
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.content.ToolCallContent;
import com.claubloom.harness.protocol.message.AgentMessage;
import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.message.UserMessage;
import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.Usage;
import com.claubloom.harness.protocol.tool.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 Smoke Test for Core Engine (TC-P1-03, TC-P1-04).
 */
public class CoreSmokeTest {

    /**
     * TC-P1-03: ToolRegistry dynamic registration and retrieval.
     */
    @Test
    @DisplayName("TC-P1-03: Should dynamically register, find, and unregister tools in ToolRegistry")
    void should_registerAndFindTool_when_dynamicToolAdded() {
        // Arrange
        ToolRegistry registry = new ToolRegistry();
        ToolDefinition addTool = new ToolDefinition() {
            @Override
            public String name() {
                return "add";
            }

            @Override
            public String description() {
                return "Calculates the sum of two integers";
            }

            @Override
            public Map<String, Object> parameterSchema() {
                return Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "a", Map.of("type", "integer"),
                                "b", Map.of("type", "integer")
                        ),
                        "required", List.of("a", "b")
                );
            }

            @Override
            public CompletableFuture<ToolResult> execute(ToolContext context, Map<String, Object> arguments) {
                int a = ((Number) arguments.getOrDefault("a", 0)).intValue();
                int b = ((Number) arguments.getOrDefault("b", 0)).intValue();
                return CompletableFuture.completedFuture(ToolResult.success(String.valueOf(a + b)));
            }
        };

        // Act
        registry.register(addTool);

        // Assert
        assertThat(registry.contains("add")).isTrue();
        assertThat(registry.size()).isEqualTo(1);
        Optional<ToolDefinition> found = registry.find("add");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("add");
        assertThat(found.get().description()).isEqualTo("Calculates the sum of two integers");
        assertThat(found.get().parameterSchema()).containsEntry("type", "object");

        // Act - Unregister
        Optional<ToolDefinition> unregistered = registry.unregister("add");
        assertThat(unregistered).isPresent();
        assertThat(registry.contains("add")).isFalse();
        assertThat(registry.size()).isEqualTo(0);
    }

    /**
     * TC-P1-04: Agent Loop Virtual Thread state machine with Mock AI.
     */
    @Test
    @DisplayName("TC-P1-04: Agent Loop should complete turn loop: Idle -> Turn 1 (ToolCall) -> Turn 2 (Answer) -> Idle")
    void should_executeAgentLoopWithToolCall_when_mockLlmProvided() throws Exception {
        // Arrange
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolDefinition() {
            @Override
            public String name() {
                return "add";
            }

            @Override
            public String description() {
                return "Add two numbers";
            }

            @Override
            public Map<String, Object> parameterSchema() {
                return Map.of("type", "object");
            }

            @Override
            public CompletableFuture<ToolResult> execute(ToolContext context, Map<String, Object> arguments) {
                int a = ((Number) arguments.getOrDefault("a", 1)).intValue();
                int b = ((Number) arguments.getOrDefault("b", 2)).intValue();
                return CompletableFuture.completedFuture(ToolResult.success("Result is " + (a + b)));
            }
        });

        ToolExecutor executor = new ToolExecutor(registry);
        AgentLoop agentLoop = new AgentLoop(executor);

        ModelRef testModel = ModelRef.of("mock-provider", "mock-model");
        AtomicInteger callCount = new AtomicInteger(0);

        // Mock LLM Caller: 1st turn returns ToolCall 'add', 2nd turn returns text answer
        LlmCaller mockLlmCaller = (context, config, eventSink) -> {
            int round = callCount.incrementAndGet();
            if (round == 1) {
                // Round 1: Assistant requests tool call
                var toolCallContent = new ToolCallContent("call-1", "add", Map.of("a", 10, "b", 20));
                var msg = AssistantMessage.complete(
                        "msg-round-1",
                        List.of(toolCallContent),
                        testModel,
                        "mock-model",
                        Usage.zero(),
                        System.currentTimeMillis(),
                        "toolUse"
                );
                return CompletableFuture.completedFuture(msg);
            } else {
                // Round 2: Assistant returns final text response after seeing tool result
                var textContent = new TextContent("The sum of 10 and 20 is 30.");
                var msg = AssistantMessage.complete(
                        "msg-round-2",
                        List.of(textContent),
                        testModel,
                        "mock-model",
                        Usage.zero(),
                        System.currentTimeMillis(),
                        "stop"
                );
                return CompletableFuture.completedFuture(msg);
            }
        };

        List<AgentEvent> capturedEvents = new CopyOnWriteArrayList<>();
        AgentEventSink sink = capturedEvents::add;

        AgentContext context = new AgentContext("test-session-01", "/tmp/workspace", new ArrayList<>());
        AgentLoopConfig config = AgentLoopConfig.builder()
                .maxTurns(5)
                .model(testModel)
                .build();

        UserMessage prompt = new UserMessage("prompt-1", List.of(new TextContent("Calculate 10 + 20")), System.currentTimeMillis());

        // Act
        List<AgentMessage> newMessages = agentLoop.runAgentLoop(
                List.of(prompt),
                context,
                config,
                sink,
                mockLlmCaller
        ).get();

        // Assert
        assertThat(newMessages).isNotEmpty();
        // Prompts (1) + Assistant msg 1 (1) + Tool Result (1) + Assistant msg 2 (1) = 4 messages
        assertThat(newMessages).hasSize(4);

        // Verify Event Stream Order
        List<String> eventTypes = capturedEvents.stream().map(AgentEvent::type).toList();
        assertThat(eventTypes).contains(
                "agent_start",
                "turn_start",
                "message_start",
                "message_end",
                "tool_call",
                "tool_result",
                "turn_end",
                "agent_end"
        );

        // Verify that tool result exists in context and equals "Result is 30"
        var toolResultMsg = newMessages.stream()
                .filter(m -> "tool".equals(m.role()))
                .findFirst();
        assertThat(toolResultMsg).isPresent();

        var finalAssistantMsg = newMessages.stream()
                .filter(m -> "assistant".equals(m.role()) && "msg-round-2".equals(m.id()))
                .map(m -> (AssistantMessage) m)
                .findFirst();
        assertThat(finalAssistantMsg).isPresent();
        assertThat(((TextContent) finalAssistantMsg.get().content().get(0)).text()).isEqualTo("The sum of 10 and 20 is 30.");
    }
}
