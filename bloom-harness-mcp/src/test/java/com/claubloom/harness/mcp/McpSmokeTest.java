package com.claubloom.harness.mcp;

import com.claubloom.harness.core.tool.ToolContext;
import com.claubloom.harness.core.tool.ToolDefinition;
import com.claubloom.harness.core.tool.ToolRegistry;
import com.claubloom.harness.mcp.bridge.McpManager;
import com.claubloom.harness.mcp.client.MockMcpClient;
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.tool.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 Smoke Tests for Model Context Protocol (MCP) Subsystem (TC-P3-05 & TC-P3-06).
 */
public class McpSmokeTest {

    private ToolRegistry toolRegistry;
    private McpManager mcpManager;
    private MockMcpClient mockClient;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        mcpManager = new McpManager(toolRegistry);
        mockClient = new MockMcpClient("sqlite-mcp-server");

        // Register simulated MCP tools on mock server
        mockClient.addTool(
                "query_sql",
                "Execute readonly SQL query",
                Map.of(
                        "type", "object",
                        "properties", Map.of("sql", Map.of("type", "string")),
                        "required", List.of("sql")
                ),
                args -> {
                    String sql = (String) args.get("sql");
                    if (sql == null || sql.isBlank()) {
                        return ToolResult.error("SQL parameter is required");
                    }
                    if (sql.toLowerCase().contains("select * from users")) {
                        return ToolResult.success("[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}]");
                    }
                    return ToolResult.success("[{\"count\":42}]");
                }
        );
    }

    @AfterEach
    void tearDown() {
        mcpManager.close();
    }

    /**
     * TC-P3-05: MCP JSON-RPC 2.0 Handshake, Capability Negotiation, and Tool Discovery.
     */
    @Test
    @DisplayName("TC-P3-05: MCP Client should perform initialize handshake and discover remote tools")
    void should_initializeAndDiscoverMcpTools() throws Exception {
        // Act - Register and mount MCP client
        mcpManager.registerClient(mockClient);

        // Assert - Tool is discovered and mounted to ToolRegistry
        assertThat(toolRegistry.contains("query_sql")).isTrue();
        ToolDefinition tool = toolRegistry.find("query_sql").orElse(null);
        assertThat(tool).isNotNull();
        assertThat(tool.name()).isEqualTo("query_sql");
        assertThat(tool.description()).isEqualTo("Execute readonly SQL query");
        assertThat(tool.parameterSchema()).containsKey("properties");
    }

    /**
     * TC-P3-06: MCP Remote Tool Execution, Parameter Marshaling, and Result Unmarshaling.
     */
    @Test
    @DisplayName("TC-P3-06: MCP Tool bridge should marshal parameters and unmarshal execution results")
    void should_executeRemoteMcpToolThroughBridge() throws Exception {
        // Arrange
        mcpManager.registerClient(mockClient);
        ToolDefinition tool = toolRegistry.find("query_sql").orElseThrow();
        ToolContext context = new ToolContext("session-mcp", ".", null, null);

        // Act - Execute remote tool with valid SQL
        ToolResult successRes = tool.execute(context, Map.of("sql", "SELECT * FROM users")).get();

        // Assert - Result is decoded into ToolResult
        assertThat(successRes.isError()).isFalse();
        assertThat(successRes.output()).isEqualTo("[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}]");

        // Act - Execute remote tool with error condition
        ToolResult errorRes = tool.execute(context, Map.of("sql", "")).get();

        // Assert - Remote error is mapped into ToolResult with error flag
        assertThat(errorRes.isError()).isTrue();
        assertThat(errorRes.output()).contains("SQL parameter is required");
    }
}
