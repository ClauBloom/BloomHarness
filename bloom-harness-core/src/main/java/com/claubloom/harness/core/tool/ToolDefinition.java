package com.claubloom.harness.core.tool;

import com.claubloom.harness.protocol.tool.ToolResult;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 智能体循环可调用的可插拔工具定义规范。
 */
public interface ToolDefinition {

    /**
     * 唯一的工具名称标识（如 bash, read, write, edit, grep, glob）。
     */
    String name();

    /**
     * 简明扼要的工具描述，向大模型解释何时以及如何调用该工具。
     */
    String description();

    /**
     * 工具所接受参数的 JSON Schema 结构定义规范。
     */
    Map<String, Object> parameterSchema();

    /**
     * 使用给定的参数执行工具逻辑。
     *
     * @param context tool execution context
     * @param arguments 从大模型工具调用中解析出的输入参数字典
     * @return 异步工具执行结果 Future
     */
    CompletableFuture<ToolResult> execute(ToolContext context, Map<String, Object> arguments);
}
