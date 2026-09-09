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
 * 内置 Edit 工具，执行带唯一性校验的精确字面量替换。
 * 默认要求目标字符串在文件中仅出现一次；可通过 replace_all 参数允许批量替换。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EditTool implements ToolDefinition {

    private final PathSandbox pathSandbox;

    @Override
    public String name() {
        return "edit";
    }

    @Override
    public String description() {
        return "Edit an existing UTF-8 text file by performing an exact literal string replacement. " +
                "You must read the file first. By default, old_string must appear exactly once in the file; " +
                "provide sufficient surrounding context lines to make old_string uniquely match, or set replace_all=true. " +
                "Do not include line number prefixes from the read tool in old_string or new_string. " +
                "Preserve exact indentation (tabs/spaces). Uses atomic write protection.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of("type", "string", "description", "Path to the existing file to edit (relative to workspace root or absolute)"),
                        "old_string", Map.of("type", "string", "description", "Exact literal text to replace, including exact whitespace and indentation. Must match uniquely unless replace_all is true."),
                        "new_string", Map.of("type", "string", "description", "Exact literal replacement text with matching indentation"),
                        "replace_all", Map.of("type", "boolean", "description", "When true, replaces all occurrences of old_string across the file (useful for variable renaming). Defaults to false.")
                ),
                "required", List.of("path", "old_string", "new_string")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(ToolContext context, Map<String, Object> arguments) {
        return CompletableFuture.supplyAsync(() -> {
            String rawPath = (String) arguments.get("path");
            if (rawPath == null || rawPath.isBlank()) {
                return ToolResult.error("Argument 'path' is required");
            }

            String oldString = (String) arguments.get("old_string");
            if (oldString == null) {
                return ToolResult.error("Argument 'old_string' is required");
            }

            String newString = (String) arguments.get("new_string");
            if (newString == null) {
                return ToolResult.error("Argument 'new_string' is required");
            }

            boolean replaceAll = Boolean.TRUE.equals(arguments.get("replace_all"));

            if (oldString.equals(newString)) {
                return ToolResult.error("Argument 'old_string' and 'new_string' must be different");
            }

            try {
                Path resolvedPath = pathSandbox.resolve(rawPath, context != null ? context.cwd() : null);
                if (!Files.exists(resolvedPath)) {
                    return ToolResult.error("File not found: " + rawPath);
                }
                if (Files.isDirectory(resolvedPath)) {
                    return ToolResult.error("Path is a directory, not a regular file: " + rawPath);
                }

                String currentContent = Files.readString(resolvedPath, StandardCharsets.UTF_8);

                int count = countOccurrences(currentContent, oldString);
                if (count == 0) {
                    return ToolResult.error("old_string was not found in " + rawPath + ". Make sure old_string matches the file's exact indentation and line breaks, and does not include line number prefixes from the read tool.");
                }

                if (!replaceAll && count > 1) {
                    return ToolResult.error(String.format(
                            "Found %d occurrences of old_string in %s. It must appear exactly once. " +
                            "Please provide more surrounding lines for unique matching or set replace_all=true.",
                            count, rawPath
                    ));
                }

                String updatedContent;
                if (replaceAll) {
                    updatedContent = currentContent.replace(oldString, newString);
                } else {
                    int index = currentContent.indexOf(oldString);
                    updatedContent = currentContent.substring(0, index) + newString + currentContent.substring(index + oldString.length());
                }

                // 原子写入
                Path tempFile = Files.createTempFile(resolvedPath.getParent(), ".tmp_edit_", ".tmp");
                try {
                    Files.writeString(tempFile, updatedContent, StandardCharsets.UTF_8);
                    try {
                        Files.move(tempFile, resolvedPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception e) {
                        Files.move(tempFile, resolvedPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(tempFile);
                }

                return ToolResult.success(String.format(
                        "Successfully replaced %d occurrence%s in %s",
                        count, count > 1 ? "s" : "", rawPath
                ));
            } catch (SecurityException se) {
                return ToolResult.error(se.getMessage());
            } catch (IOException ioe) {
                return ToolResult.error("Failed to edit file: " + ioe.getMessage());
            } catch (Exception e) {
                return ToolResult.error("Error executing edit: " + e.getMessage());
            }
        });
    }

    private int countOccurrences(String text, String target) {
        if (target.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
