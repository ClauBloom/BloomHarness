package com.claubloom.harness.mcp.client;

import com.claubloom.harness.mcp.model.*;
import com.claubloom.harness.protocol.content.ImageContent;
import com.claubloom.harness.protocol.content.MessageContent;
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stdio-based MCP Client communicating with external MCP server process over JSON-RPC 2.0.
 */
@Slf4j
public class StdioMcpClient implements McpClient {

    @Getter
    private final String serverName;
    private final McpServerConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong requestIdSequence = new AtomicLong(1);
    private final Map<Long, CompletableFuture<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();

    private Process process;
    private BufferedWriter stdinWriter;
    private Thread stdoutReaderThread;

    public StdioMcpClient(McpServerConfig config) {
        this.serverName = config.name();
        this.config = config;
    }

    public synchronized void start() throws IOException {
        if (process != null && process.isAlive()) {
            return;
        }

        List<String> command = new ArrayList<>();
        command.add(config.command());
        if (config.args() != null) {
            command.addAll(config.args());
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        if (config.env() != null) {
            pb.environment().putAll(config.env());
        }
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        this.process = pb.start();
        this.stdinWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        this.stdoutReaderThread = Thread.ofVirtual().start(this::readStdoutLoop);
        log.info("Started MCP Stdio Client for '{}' (PID: {})", serverName, process.pid());
    }

    private void readStdoutLoop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    JsonRpcResponse response = objectMapper.readValue(line, JsonRpcResponse.class);
                    if (response.id() != null) {
                        long reqId = Long.parseLong(response.id().toString());
                        CompletableFuture<JsonRpcResponse> future = pendingRequests.remove(reqId);
                        if (future != null) {
                            future.complete(response);
                        }
                    }
                } catch (Exception e) {
                    log.warn("MCP Server '{}' emitted invalid JSON-RPC line: {}", serverName, line);
                }
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public CompletableFuture<Map<String, Object>> initialize() {
        long id = requestIdSequence.getAndIncrement();
        Map<String, Object> params = Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of("tools", Map.of()),
                "clientInfo", Map.of("name", "BloomHarness", "version", "1.0.0")
        );

        JsonRpcRequest req = new JsonRpcRequest(id, "initialize", params);
        return sendRequest(id, req).thenApply(resp -> {
            if (resp.isError()) {
                throw new RuntimeException("MCP initialize failed: " + resp.error().message());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> res = (Map<String, Object>) resp.result();
            return res != null ? res : Map.of();
        });
    }

    @Override
    public CompletableFuture<List<McpToolInfo>> listTools() {
        long id = requestIdSequence.getAndIncrement();
        JsonRpcRequest req = new JsonRpcRequest(id, "tools/list", Map.of());

        return sendRequest(id, req).thenApply(resp -> {
            if (resp.isError()) {
                throw new RuntimeException("MCP tools/list failed: " + resp.error().message());
            }

            List<McpToolInfo> tools = new ArrayList<>();
            JsonNode rootNode = objectMapper.valueToTree(resp.result());
            JsonNode toolsNode = rootNode.get("tools");
            if (toolsNode != null && toolsNode.isArray()) {
                for (JsonNode t : toolsNode) {
                    String name = t.path("name").asText();
                    String description = t.path("description").asText("");
                    JsonNode schemaNode = t.path("inputSchema");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> schema = objectMapper.convertValue(schemaNode, Map.class);
                    tools.add(new McpToolInfo(name, description, schema));
                }
            }
            return tools;
        });
    }

    @Override
    public CompletableFuture<ToolResult> callTool(String name, Map<String, Object> arguments) {
        long id = requestIdSequence.getAndIncrement();
        Map<String, Object> params = Map.of(
                "name", name,
                "arguments", arguments != null ? arguments : Map.of()
        );

        JsonRpcRequest req = new JsonRpcRequest(id, "tools/call", params);
        return sendRequest(id, req).thenApply(resp -> {
            if (resp.isError()) {
                return ToolResult.error(resp.error().message());
            }

            JsonNode rootNode = objectMapper.valueToTree(resp.result());
            boolean isError = rootNode.path("isError").asBoolean(false);
            JsonNode contentArray = rootNode.path("content");

            List<MessageContent> contents = new ArrayList<>();
            if (contentArray.isArray()) {
                for (JsonNode item : contentArray) {
                    String type = item.path("type").asText("text");
                    if ("text".equals(type)) {
                        contents.add(new TextContent(item.path("text").asText("")));
                    } else if ("image".equals(type)) {
                        contents.add(new ImageContent(item.path("data").asText(""), item.path("mimeType").asText("image/png")));
                    }
                }
            }

            if (contents.isEmpty()) {
                contents.add(new TextContent(resp.result() != null ? resp.result().toString() : "(no content)"));
            }

            return isError ? ToolResult.error(contents) : ToolResult.success(contents);
        });
    }

    private CompletableFuture<JsonRpcResponse> sendRequest(long id, JsonRpcRequest request) {
        CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        try {
            String json = objectMapper.writeValueAsString(request) + "\n";
            synchronized (this) {
                if (stdinWriter == null) {
                    start();
                }
                stdinWriter.write(json);
                stdinWriter.flush();
            }
        } catch (Exception e) {
            pendingRequests.remove(id);
            future.completeExceptionally(e);
        }

        return future.orTimeout(30, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        if (process != null) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process = null;
        }
        if (stdoutReaderThread != null) {
            stdoutReaderThread.interrupt();
        }
    }
}
