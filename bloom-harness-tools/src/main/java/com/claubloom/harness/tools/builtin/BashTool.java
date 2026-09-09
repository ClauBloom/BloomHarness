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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 内置 Bash 工具，在子进程中执行 shell 命令，支持可配置的超时与尾部输出截断。
 * 超时时强制终止进程树；非零退出码会在输出末尾追加 [exit code: N] 标记。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BashTool implements ToolDefinition {

    private final PathSandbox pathSandbox;
    private static final long DEFAULT_TIMEOUT_MS = 60_000L; // 默认超时 60 秒
    private static final long MAX_TIMEOUT_MS = 600_000L;    // 最长超时 10 分钟

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public String description() {
        return "Execute a shell command in the workspace and return combined stdout/stderr. " +
                "On Windows commands run via cmd.exe /c; on Unix via bash -c. " +
                "Non-zero exit codes append [exit code: N]. Long outputs are truncated at the tail. " +
                "DO NOT use for file operations (reading, writing, editing, searching files) - always use read, edit, write, glob, or grep instead. " +
                "Avoid interactive commands, pagers, and prompts as they will hang.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "command", Map.of("type", "string", "description", "The shell command to execute non-interactively"),
                        "workdir", Map.of("type", "string", "description", "Working directory relative to workspace root or absolute path. Defaults to workspace root."),
                        "timeoutMs", Map.of("type", "integer", "description", "Execution timeout in milliseconds (10 to 600000 ms, default 60000 ms)")
                ),
                "required", List.of("command")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(ToolContext context, Map<String, Object> arguments) {
        return CompletableFuture.supplyAsync(() -> {
            String command = (String) arguments.get("command");
            if (command == null || command.isBlank()) {
                return ToolResult.error("Argument 'command' is required");
            }

            String workdirStr = (String) arguments.get("workdir");
            long timeoutMs = DEFAULT_TIMEOUT_MS;
            if (arguments.get("timeoutMs") instanceof Number num) {
                timeoutMs = Math.min(MAX_TIMEOUT_MS, Math.max(10L, num.longValue()));
            } else if (arguments.get("timeout") instanceof Number num) {
                timeoutMs = Math.min(MAX_TIMEOUT_MS, Math.max(10L, (long) (num.doubleValue() * 1000)));
            }

            Path workingDirectory;
            try {
                String targetWorkdir = workdirStr != null && !workdirStr.isBlank() ? workdirStr : ".";
                workingDirectory = pathSandbox.resolve(targetWorkdir, context != null ? context.cwd() : null);
                if (!Files.isDirectory(workingDirectory)) {
                    return ToolResult.error("Working directory not found: " + workingDirectory);
                }
            } catch (SecurityException se) {
                return ToolResult.error(se.getMessage());
            }

            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            List<String> processCmd = isWindows
                    ? List.of("cmd.exe", "/c", command)
                    : List.of("bash", "-c", command);

            ProcessBuilder pb = new ProcessBuilder(processCmd);
            pb.directory(workingDirectory.toFile());
            pb.redirectErrorStream(true); // 将 stderr 合并到 stdout 流中

            Process process = null;
            try {
                process = pb.start();
                final Process activeProcess = process;

                StringBuilder outputBuffer = new StringBuilder();
                Thread readerThread = Thread.ofVirtual().start(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeProcess.getInputStream(), StandardCharsets.UTF_8))) {
                        char[] buffer = new char[4096];
                        int read;
                        while ((read = reader.read(buffer)) != -1) {
                            outputBuffer.append(buffer, 0, read);
                        }
                    } catch (IOException ignored) {
                    }
                });

                boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                if (!finished) {
                    // 强制终止进程及其全部后代子进程
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                    readerThread.interrupt();
                    return ToolResult.error(
                            String.format("Command timed out after %d ms:\n%s\n[timed out after %dms]", timeoutMs, outputBuffer, timeoutMs));
                }

                try {
                    readerThread.join(Duration.ofSeconds(1));
                } catch (InterruptedException ignored) {
                }

                int exitCode = process.exitValue();
                String rawOutput = outputBuffer.toString();

                OutputTruncator.TruncationResult truncation = OutputTruncator.truncateTail(rawOutput);
                String resultText = truncation.content();

                if (truncation.truncated()) {
                    resultText = "[output truncated]\n" + resultText;
                }

                if (exitCode != 0) {
                    if (!resultText.isEmpty() && !resultText.endsWith("\n")) {
                        resultText += "\n";
                    }
                    resultText += String.format("[exit code: %d]", exitCode);
                    return ToolResult.error(resultText);
                }

                return ToolResult.success(resultText.isEmpty() ? "(no output)" : resultText);
            } catch (Exception e) {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                }
                return ToolResult.error("Command execution failed: " + e.getMessage());
            }
        });
    }
}
