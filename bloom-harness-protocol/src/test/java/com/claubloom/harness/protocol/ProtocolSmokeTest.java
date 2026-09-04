package com.claubloom.harness.protocol;

import com.claubloom.harness.protocol.command.*;
import com.claubloom.harness.protocol.content.*;
import com.claubloom.harness.protocol.envelope.*;
import com.claubloom.harness.protocol.message.*;
import com.claubloom.harness.protocol.model.*;
import com.claubloom.harness.protocol.result.*;
import com.claubloom.harness.protocol.session.*;
import com.claubloom.harness.protocol.stream.*;
import com.claubloom.harness.protocol.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 Smoke Test for Protocol Models (TC-P1-01, TC-P1-02).
 */
public class ProtocolSmokeTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * TC-P1-01: Message union type JSON round-trip serialization and deserialization.
     */
    @Test
    @DisplayName("TC-P1-01: Should serialize and deserialize composite AssistantMessage with Text, Thinking, and ToolCall content")
    void should_serializeAndDeserializeAssistantMessage_when_compositeContent() throws Exception {
        // Arrange
        var model = ModelRef.of("anthropic", "claude-3-5-sonnet");
        var usage = new Usage(100, 50, 10, 5, 20, 150, new ModelCost(0.003, 0.015, 0.0003, 0.00375, 0.02205));
        List<MessageContent> content = List.of(
                new TextContent("Let me analyze the problem first."),
                new ThinkingContent("Need to check the file contents before editing.", false),
                new ToolCallContent("call-001", "read", Map.of("path", "/workspace/test.txt"))
        );

        var originalMessage = AssistantMessage.complete(
                "msg-12345",
                content,
                model,
                "claude-3-5-sonnet-20241022",
                usage,
                System.currentTimeMillis(),
                "toolUse"
        );

        // Act
        String json = objectMapper.writeValueAsString(originalMessage);
        AgentMessage deserialized = objectMapper.readValue(json, AgentMessage.class);

        // Assert
        assertThat(deserialized).isInstanceOf(AssistantMessage.class);
        var assistantMessage = (AssistantMessage) deserialized;
        assertThat(assistantMessage.id()).isEqualTo("msg-12345");
        assertThat(assistantMessage.role()).isEqualTo("assistant");
        assertThat(assistantMessage.model().provider()).isEqualTo("anthropic");
        assertThat(assistantMessage.model().id()).isEqualTo("claude-3-5-sonnet");
        assertThat(assistantMessage.stopReason()).isEqualTo("toolUse");
        assertThat(assistantMessage.content()).hasSize(3);

        assertThat(assistantMessage.content().get(0)).isInstanceOf(TextContent.class);
        assertThat(((TextContent) assistantMessage.content().get(0)).text()).isEqualTo("Let me analyze the problem first.");

        assertThat(assistantMessage.content().get(1)).isInstanceOf(ThinkingContent.class);
        assertThat(((ThinkingContent) assistantMessage.content().get(1)).thinking()).isEqualTo("Need to check the file contents before editing.");

        assertThat(assistantMessage.content().get(2)).isInstanceOf(ToolCallContent.class);
        var toolCall = (ToolCallContent) assistantMessage.content().get(2);
        assertThat(toolCall.toolCallId()).isEqualTo("call-001");
        assertThat(toolCall.toolName()).isEqualTo("read");

        // Verify Server Envelope Round-trip
        var serverEvent = new ServerEvent.SessionProgressEvent(
                "sess-01",
                new TranscriptProgress.AssistantDelta("msg-12345", 0, "text", "Hello delta")
        );
        var eventEnvelope = new EventEnvelope(serverEvent);
        String envelopeJson = objectMapper.writeValueAsString(eventEnvelope);
        ServerMessage deserializedEnvelope = objectMapper.readValue(envelopeJson, ServerMessage.class);
        assertThat(deserializedEnvelope).isInstanceOf(EventEnvelope.class);
        var env = (EventEnvelope) deserializedEnvelope;
        assertThat(env.event()).isInstanceOf(ServerEvent.SessionProgressEvent.class);
    }

    /**
     * TC-P1-02: Result pattern matching and monadic chaining.
     */
    @Test
    @DisplayName("TC-P1-02: Should unpack and map Result.Ok and Result.Err correctly without exceptions")
    void should_unpackAndMapResult_when_okOrErr() {
        // Test Result.Ok
        Result<String> okResult = Result.ok("operation-successful");
        assertThat(okResult.isOk()).isTrue();
        assertThat(okResult.isErr()).isFalse();
        assertThat(okResult.getOrNull()).isEqualTo("operation-successful");
        assertThat(okResult.getErrorOrNull()).isNull();

        Result<Integer> mappedOk = okResult.map(String::length);
        assertThat(mappedOk.getOrNull()).isEqualTo("operation-successful".length());

        // Pattern matching
        String okUnpacked = switch (okResult) {
            case Result.Ok<String> ok -> "Received: " + ok.value();
            case Result.Err<String> err -> "Error: " + err.error().message();
        };
        assertThat(okUnpacked).isEqualTo("Received: operation-successful");

        // Test Result.Err
        Result<String> errResult = Result.err("NOT_FOUND", "File does not exist");
        assertThat(errResult.isOk()).isFalse();
        assertThat(errResult.isErr()).isTrue();
        assertThat(errResult.getOrNull()).isNull();
        assertThat(errResult.getErrorOrNull()).isNotNull();
        assertThat(errResult.getErrorOrNull().code()).isEqualTo("NOT_FOUND");
        assertThat(errResult.getErrorOrNull().message()).isEqualTo("File does not exist");

        Result<Integer> mappedErr = errResult.map(String::length);
        assertThat(mappedErr.isErr()).isTrue();
        assertThat(mappedErr.getErrorOrNull().code()).isEqualTo("NOT_FOUND");

        String errUnpacked = switch (errResult) {
            case Result.Ok<String> ok -> "Received: " + ok.value();
            case Result.Err<String> err -> "Error: " + err.error().message();
        };
        assertThat(errUnpacked).isEqualTo("Error: File does not exist");
    }
}
