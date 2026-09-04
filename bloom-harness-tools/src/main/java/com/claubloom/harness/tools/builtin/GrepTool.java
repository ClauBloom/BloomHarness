package com.claubloom.harness.tools.builtin;

import com.claubloom.harness.core.tool.ToolContext;
import com.claubloom.harness.core.tool.ToolDefinition;
import com.claubloom.harness.protocol.tool.ToolResult;
import com.claubloom.harness.tools.sandbox.PathSandbox;
import com.claubloom.harness.tools.truncate.OutputTruncator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Built-in Grep tool for recursive regex / literal content search.
 * 严格对齐 pi-agent 的 grep.ts 实现规范。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrepTool implements ToolDefinition {

    private final PathSandbox pathSandbox;
    private static final int DEFAULT_LIMIT = 250;
    private static final Set<String> IGNORED_DIRS = Set.of(".git", "node_modules", "target", ".m2-repo", ".idea", ".vscode");

    @Override
    public String name() {
        return "grep";
    }

    @Override
    public String description() {
        return "Search file contents with a regular expression. Returns matching lines with line numbers, grouped by file.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "pattern", Map.of("type", "string", "description", "Regular expression to search for"),
                        "path", Map.of("type", "string", "description", "File or directory to search. Defaults to workspace root."),
                        "include", Map.of("type", "string", "description", "Glob filter for files to search (e.g. '*.java', '**/*.xml')"),
                        "ignoreCase", Map.of("type", "boolean", "description", "Case-insensitive matching. Defaults to false."),
                        "limit", Map.of("type", "integer", "description", "Maximum matching lines to return. Defaults to 250.")
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
            String includeGlob = (String) arguments.get("include");
            boolean ignoreCase = Boolean.TRUE.equals(arguments.get("ignoreCase"));
            int limit = DEFAULT_LIMIT;
            if (arguments.get("limit") instanceof Number num) {
                limit = Math.max(1, num.intValue());
            }

            Pattern regexPattern;
            try {
                int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
                regexPattern = Pattern.compile(rawPattern, flags);
            } catch (PatternSyntaxException pse) {
                return ToolResult.error("Invalid regular expression: " + pse.getMessage());
            }

            try {
                String targetSearchPath = searchPathStr != null && !searchPathStr.isBlank() ? searchPathStr : ".";
                Path searchRoot = pathSandbox.resolve(targetSearchPath, context != null ? context.cwd() : null);
                if (!Files.exists(searchRoot)) {
                    return ToolResult.error("Search path not found: " + targetSearchPath);
                }

                PathMatcher pathMatcher = null;
                if (includeGlob != null && !includeGlob.isBlank()) {
                    String globPattern = includeGlob.contains("/") ? "glob:" + includeGlob : "glob:**/" + includeGlob;
                    pathMatcher = FileSystems.getDefault().getPathMatcher(globPattern);
                }

                List<String> results = new ArrayList<>();
                int[] matchCount = new int[]{0};
                int finalLimit = limit;
                PathMatcher finalPathMatcher = pathMatcher;

                if (Files.isRegularFile(searchRoot)) {
                    grepFile(searchRoot, regexPattern, searchRoot.getParent(), results, matchCount, finalLimit);
                } else {
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
                            if (matchCount[0] >= finalLimit) {
                                return FileVisitResult.TERMINATE;
                            }

                            if (!attrs.isRegularFile()) {
                                return FileVisitResult.CONTINUE;
                            }

                            if (finalPathMatcher != null && !finalPathMatcher.matches(file) && !finalPathMatcher.matches(searchRoot.relativize(file))) {
                                return FileVisitResult.CONTINUE;
                            }

                            // Skip binary or huge files (>10MB)
                            if (attrs.size() > 10 * 1024 * 1024) {
                                return FileVisitResult.CONTINUE;
                            }

                            grepFile(file, regexPattern, searchRoot, results, matchCount, finalLimit);

                            if (matchCount[0] >= finalLimit) {
                                return FileVisitResult.TERMINATE;
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }

                if (results.isEmpty()) {
                    return ToolResult.success("No matches found");
                }

                StringBuilder output = new StringBuilder();
                output.append(String.format("Found %d match%s:\n\n", matchCount[0], matchCount[0] > 1 ? "es" : ""));
                output.append(String.join("\n", results));
                if (matchCount[0] >= finalLimit) {
                    output.append(String.format("\n\n[Matched first %d results. Limit reached.]", finalLimit));
                }

                return ToolResult.success(output.toString());
            } catch (SecurityException se) {
                return ToolResult.error(se.getMessage());
            } catch (Exception e) {
                return ToolResult.error("Error executing grep: " + e.getMessage());
            }
        });
    }

    private void grepFile(Path file, Pattern pattern, Path baseDir, List<String> results, int[] matchCount, int limit) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNum = 0;
            boolean fileHeaderAdded = false;
            Path displayPath = baseDir != null ? baseDir.relativize(file) : file;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    if (!fileHeaderAdded) {
                        if (!results.isEmpty()) {
                            results.add("");
                        }
                        results.add(displayPath.toString().replace('\\', '/') + ":");
                        fileHeaderAdded = true;
                    }

                    // Truncate long lines to OutputTruncator.GREP_MAX_LINE_LENGTH
                    String displayLine = line;
                    if (displayLine.length() > OutputTruncator.GREP_MAX_LINE_LENGTH) {
                        displayLine = displayLine.substring(0, OutputTruncator.GREP_MAX_LINE_LENGTH) + "...";
                    }

                    results.add(String.format("  Line %d: %s", lineNum, displayLine));
                    matchCount[0]++;

                    if (matchCount[0] >= limit) {
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
            // Ignore unreadable binary or permission error files
        }
    }
}
