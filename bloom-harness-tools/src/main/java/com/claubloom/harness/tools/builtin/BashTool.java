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
 * Built-in Bash tool for executing shell commands with timeout and tail truncation.
 * 严格对齐 pi-agent 的 bash.ts 实现规范。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BashTool implements ToolDefinition {

    private final PathSandbox pathSandbox;
    private static final long DEFAULT_TIMEOUT_MS = 60_000L; // 60s default timeout
    private static final long MAX_TIMEOUT_MS = 600_000L;    // 10min max timeout

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public String description() {
        return "Execute a bash shell command and return its stdout/stderr. Non-zero exits are reported as [exit code: N].";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "command", Map.of("type", "string", "description", "The shell command to execute"),
                        "workdir", Map.of("type", "string", "description", "Working directory for the command. Defaults to workspace root."),
                        "timeoutMs", Map.of("type", "integer", "description", "Command timeout in milliseconds. Defaults to 60000ms (60s).")
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
            pb.redirectErrorStream(true); // Merge stderr into stdout stream

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
                    // Forcefully terminate process and all descendant subprocesses (mirrors pi's killProcessTree)
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
