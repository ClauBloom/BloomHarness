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
 * Model Context Protocol (MCP) 子系统的第 3 阶段冒烟测试（TC-P3-05 与 TC-P3-06）。
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

        // 在 mock 服务器上注册模拟 MCP 工具
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
     * TC-P3-05: MCP JSON-RPC 2.0 握手、能力协商与工具发现。
     */
    @Test
    @DisplayName("TC-P3-05: MCP Client should perform initialize handshake and discover remote tools")
    void should_initializeAndDiscoverMcpTools() throws Exception {
        // 执行 - 注册并挂载 MCP 客户端
        mcpManager.registerClient(mockClient);

        // 断言 - 工具被发现并挂载到 ToolRegistry
        assertThat(toolRegistry.contains("query_sql")).isTrue();
        ToolDefinition tool = toolRegistry.find("query_sql").orElse(null);
        assertThat(tool).isNotNull();
        assertThat(tool.name()).isEqualTo("query_sql");
        assertThat(tool.description()).isEqualTo("Execute readonly SQL query");
        assertThat(tool.parameterSchema()).containsKey("properties");
    }

    /**
     * TC-P3-06: MCP 远程工具执行、参数封送与结果反封送。
     */
    @Test
    @DisplayName("TC-P3-06: MCP Tool bridge should marshal parameters and unmarshal execution results")
    void should_executeRemoteMcpToolThroughBridge() throws Exception {
        // 准备
        mcpManager.registerClient(mockClient);
        ToolDefinition tool = toolRegistry.find("query_sql").orElseThrow();
        ToolContext context = new ToolContext("session-mcp", ".", null, null);

        // 执行 - 使用有效 SQL 执行远程工具
        ToolResult successRes = tool.execute(context, Map.of("sql", "SELECT * FROM users")).get();

        // 断言 - 结果被解码为 ToolResult
        assertThat(successRes.isError()).isFalse();
        assertThat(successRes.output()).isEqualTo("[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}]");

        // 执行 - 在错误条件下执行远程工具
        ToolResult errorRes = tool.execute(context, Map.of("sql", "")).get();

        // 断言 - 远程错误被映射为带错误标志的 ToolResult
        assertThat(errorRes.isError()).isTrue();
        assertThat(errorRes.output()).contains("SQL parameter is required");
    }
}
