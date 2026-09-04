package com.claubloom.harness.tools.builtin;

import com.claubloom.harness.core.tool.ToolContext;
import com.claubloom.harness.core.tool.ToolDefinition;
import com.claubloom.harness.protocol.tool.ToolResult;
import com.claubloom.harness.tools.sandbox.PathSandbox;
import com.claubloom.harness.tools.truncate.OutputTruncator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Built-in Read tool with line numbering, pagination (offset/limit), and head truncation.
 * 严格对齐 pi-agent 的 read.ts 实现规范。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadTool implements ToolDefinition {

    private final PathSandbox pathSandbox;

    @Override
    public String name() {
        return "read";
    }

    @Override
    public String description() {
        return "Read UTF-8 text file contents with line numbers. Use offset and limit to paginate through large files.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of("type", "string", "description", "Path to the file to read (relative or absolute)"),
                        "offset", Map.of("type", "integer", "description", "Line number to start reading from (1-indexed)"),
                        "limit", Map.of("type", "integer", "description", "Maximum number of lines to read")
                ),
                "required", List.of("path")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(ToolContext context, Map<String, Object> arguments) {
        return CompletableFuture.supplyAsync(() -> {
            String rawPath = (String) arguments.get("path");
            if (rawPath == null || rawPath.isBlank()) {
                return ToolResult.error("Argument 'path' is required");
            }

            Integer offset = null;
            if (arguments.get("offset") instanceof Number num) {
                offset = num.intValue();
            }

            Integer limit = null;
            if (arguments.get("limit") instanceof Number num) {
                limit = num.intValue();
            }

            try {
                Path resolvedPath = pathSandbox.resolve(rawPath, context != null ? context.cwd() : null);
                if (!Files.exists(resolvedPath)) {
                    return ToolResult.error("File not found: " + rawPath);
                }
                if (Files.isDirectory(resolvedPath)) {
                    return ToolResult.error("Path is a directory, not a regular file: " + rawPath);
                }

                List<String> allLines = Files.readAllLines(resolvedPath, StandardCharsets.UTF_8);
                int totalLines = allLines.size();

                int startLine = offset != null ? Math.max(0, offset - 1) : 0;
                int startDisplay = startLine + 1;

                if (totalLines == 0) {
                    return ToolResult.success("(empty file)");
                }

                if (startLine >= totalLines) {
                    return ToolResult.error(
                            String.format("Offset %d is beyond end of file (%d lines total)", offset, totalLines));
                }

                int endLine = limit != null ? Math.min(startLine + limit, totalLines) : totalLines;
                int userLinesCount = endLine - startLine;

                List<String> formattedLines = new ArrayList<>();
                for (int i = startLine; i < endLine; i++) {
                    formattedLines.add(String.format("%4d: %s", i + 1, allLines.get(i)));
                }

                String selectedContent = String.join("\n", formattedLines);
                OutputTruncator.TruncationResult truncation = OutputTruncator.truncateHead(selectedContent);

                String outputText;
                if (truncation.firstLineExceedsLimit()) {
                    outputText = String.format("[Line %d exceeds limit. File line is too long to display safely.]", startDisplay);
                } else if (truncation.truncated()) {
                    int outputLines = truncation.outputLines();
                    int endDisplay = startDisplay + outputLines - 1;
                    int nextOffset = endDisplay + 1;
                    outputText = truncation.content() + String.format(
                            "\n\n[Showing lines %d-%d of %d. Use offset=%d to continue.]",
                            startDisplay, endDisplay, totalLines, nextOffset);
                } else if (limit != null && startLine + userLinesCount < totalLines) {
                    int remaining = totalLines - (startLine + userLinesCount);
                    int nextOffset = startLine + userLinesCount + 1;
                    outputText = truncation.content() + String.format(
                            "\n\n[%d more lines in file. Use offset=%d to continue.]",
                            remaining, nextOffset);
                } else {
                    outputText = truncation.content();
                }

                return ToolResult.success(outputText);
            } catch (SecurityException se) {
                return ToolResult.error(se.getMessage());
            } catch (IOException ioe) {
                return ToolResult.error("Failed to read file: " + ioe.getMessage());
            } catch (Exception e) {
                return ToolResult.error("Error executing read: " + e.getMessage());
            }
        });
    }
}
