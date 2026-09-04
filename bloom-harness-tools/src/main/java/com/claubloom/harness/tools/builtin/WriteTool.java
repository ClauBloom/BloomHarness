package com.claubloom.harness.tools.builtin;

import com.claubloom.harness.core.tool.ToolContext;
import com.claubloom.harness.core.tool.ToolDefinition;
import com.claubloom.harness.protocol.tool.ToolResult;
import com.claubloom.harness.tools.sandbox.PathSandbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 内置 Write 文件写入工具，以原子方式创建或覆盖文件内容。
 * 严格对齐 pi-agent 的 write.ts 实现规范。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WriteTool implements ToolDefinition {

    private final PathSandbox pathSandbox;

    @Override
    public String name() {
        return "write";
    }

    @Override
    public String description() {
        return "创建或完全替换 UTF-8 文本文件。自动创建父级目录，采用原子写入保护。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of("type", "string", "description", "Path to the file to write (relative or absolute)"),
                        "content", Map.of("type", "string", "description", "Full UTF-8 content to write into the file")
                ),
                "required", List.of("path", "content")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(ToolContext context, Map<String, Object> arguments) {
        return CompletableFuture.supplyAsync(() -> {
            String rawPath = (String) arguments.get("path");
            if (rawPath == null || rawPath.isBlank()) {
                return ToolResult.error("Argument 'path' is required");
            }

            String content = (String) arguments.get("content");
            if (content == null) {
                return ToolResult.error("Argument 'content' is required");
            }

            try {
                Path resolvedPath = pathSandbox.resolve(rawPath, context != null ? context.cwd() : null);
                if (Files.exists(resolvedPath) && Files.isDirectory(resolvedPath)) {
                    return ToolResult.error("Target path is an existing directory: " + rawPath);
                }

                // Ensure parent directory exists
                Path parent = resolvedPath.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }

                // Atomic write: write to temp file in the same directory, then rename
                Path tempFile = Files.createTempFile(parent != null ? parent : resolvedPath.getParent(), ".tmp_write_", ".tmp");
                try {
                    Files.writeString(tempFile, content, StandardCharsets.UTF_8);
                    try {
                        Files.move(tempFile, resolvedPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception e) {
                        // Fallback if atomic move across filesystems is unsupported
                        Files.move(tempFile, resolvedPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(tempFile);
                }

                int byteCount = content.getBytes(StandardCharsets.UTF_8).length;
                int lineCount = content.split("\n", -1).length;
                return ToolResult.success(
                        String.format("Successfully wrote %d bytes (%d lines) to %s", byteCount, lineCount, rawPath));
            } catch (SecurityException se) {
                return ToolResult.error(se.getMessage());
            } catch (IOException ioe) {
                return ToolResult.error("Failed to write file: " + ioe.getMessage());
            } catch (Exception e) {
                return ToolResult.error("Error executing write: " + e.getMessage());
            }
        });
    }
}
