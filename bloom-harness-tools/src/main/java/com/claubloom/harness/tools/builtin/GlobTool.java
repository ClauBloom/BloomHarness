package com.claubloom.harness.tools.builtin;

import com.claubloom.harness.core.tool.ToolContext;
import com.claubloom.harness.core.tool.ToolDefinition;
import com.claubloom.harness.protocol.tool.ToolResult;
import com.claubloom.harness.tools.sandbox.PathSandbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Built-in Glob tool for discovering files matching pattern.
 * Directly mirrors pi's find.ts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GlobTool implements ToolDefinition {

    private final PathSandbox pathSandbox;
    private static final int DEFAULT_LIMIT = 100;
    private static final Set<String> IGNORED_DIRS = Set.of(".git", "node_modules", "target", ".m2-repo", ".idea", ".vscode");

    @Override
    public String name() {
        return "glob";
    }

    @Override
    public String description() {
        return "Find files whose paths match a glob pattern. Returns matching file paths relative to search directory.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "pattern", Map.of("type", "string", "description", "Glob pattern to match file paths against (e.g. '**/*.java', 'src/**/*.xml')"),
                        "path", Map.of("type", "string", "description", "Directory to search in. Defaults to workspace root."),
                        "limit", Map.of("type", "integer", "description", "Maximum number of file paths to return. Defaults to 100.")
                ),
                "required", List.of("pattern")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(ToolContext context, Map<String, Object> arguments) {
        return CompletableFuture.supplyAsync(() -> {
            String rawPattern = (String) arguments.get("pattern");
            if (rawPattern == null || rawPattern.isBlank()) {
                return ToolResult.error("Argument 'pattern' is required");
            }

            String searchPathStr = (String) arguments.get("path");
            int limit = DEFAULT_LIMIT;
            if (arguments.get("limit") instanceof Number num) {
                limit = Math.max(1, num.intValue());
            }

            try {
                String targetSearchPath = searchPathStr != null && !searchPathStr.isBlank() ? searchPathStr : ".";
                Path searchRoot = pathSandbox.resolve(targetSearchPath, context != null ? context.cwd() : null);
                if (!Files.exists(searchRoot)) {
                    return ToolResult.error("Search path not found: " + targetSearchPath);
                }
                if (!Files.isDirectory(searchRoot)) {
                    return ToolResult.error("Search path is not a directory: " + searchRoot);
                }

                String globSyntax = rawPattern.contains("/") ? "glob:" + rawPattern : "glob:**/" + rawPattern;
                PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(globSyntax);

                List<String> matches = new ArrayList<>();
                int finalLimit = limit;

                Files.walkFileTree(searchRoot, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                        if (IGNORED_DIRS.contains(dirName)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (matches.size() >= finalLimit) {
                            return FileVisitResult.TERMINATE;
                        }

                        if (!attrs.isRegularFile()) {
                            return FileVisitResult.CONTINUE;
                        }

                        Path relativePath = searchRoot.relativize(file);
                        if (pathMatcher.matches(file) || pathMatcher.matches(relativePath)) {
                            matches.add(relativePath.toString().replace('\\', '/'));
                        }

                        if (matches.size() >= finalLimit) {
                            return FileVisitResult.TERMINATE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });

                if (matches.isEmpty()) {
                    return ToolResult.success("No files matched pattern: " + rawPattern);
                }

                StringBuilder output = new StringBuilder();
                output.append(String.join("\n", matches));
                if (matches.size() >= finalLimit) {
                    output.append(String.format("\n\n[Reached limit of %d matches. Refine pattern if needed.]", finalLimit));
                }

                return ToolResult.success(output.toString());
            } catch (SecurityException se) {
                return ToolResult.error(se.getMessage());
            } catch (Exception e) {
                return ToolResult.error("Error executing glob: " + e.getMessage());
            }
        });
    }
}
