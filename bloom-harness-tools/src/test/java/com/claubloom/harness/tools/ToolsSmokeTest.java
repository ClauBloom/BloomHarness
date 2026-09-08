package com.claubloom.harness.tools;

import com.claubloom.harness.core.tool.ToolContext;
import com.claubloom.harness.protocol.tool.ToolResult;
import com.claubloom.harness.tools.builtin.*;
import com.claubloom.harness.tools.sandbox.PathSandbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 内置系统工具的第 2 阶段冒烟测试（TC-P2-03 ~ TC-P2-06）。
 * 直接对应 pi 的工具测试套件。
 */
public class ToolsSmokeTest {

    @TempDir
    Path tempWorkspace;

    private PathSandbox sandbox;
    private ReadTool readTool;
    private WriteTool writeTool;
    private EditTool editTool;
    private GrepTool grepTool;
    private GlobTool globTool;
    private BashTool bashTool;

    @BeforeEach
    void setUp() {
        sandbox = new PathSandbox(tempWorkspace);
        readTool = new ReadTool(sandbox);
        writeTool = new WriteTool(sandbox);
        editTool = new EditTool(sandbox);
        grepTool = new GrepTool(sandbox);
        globTool = new GlobTool(sandbox);
        bashTool = new BashTool(sandbox);
    }

    private ToolContext createContext(String id) {
        return new ToolContext("session-1", tempWorkspace.toString(), null, null);
    }

    /**
     * TC-P2-03: 只读沙箱的路径遍历防护。
     */
    @Test
    @DisplayName("TC-P2-03: PathSandbox should strictly prevent directory traversal escapes")
    void should_preventDirectoryTraversal() {
        // 使用 ../.. 进行目录遍历尝试
        assertThatThrownBy(() -> sandbox.resolve("../../etc/passwd"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path sandbox violation");

        // 工作区之外的绝对路径
        assertThatThrownBy(() -> sandbox.resolve("/etc/shadow"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path sandbox violation");

        // 工作区内的合法相对路径
        Path valid = sandbox.resolve("subdir/file.txt");
        assertThat(valid.startsWith(tempWorkspace)).isTrue();
    }

    /**
     * TC-P2-04: 文件操作（读、写、编辑），含精确替换与唯一性校验提示。
     */
    @Test
    @DisplayName("TC-P2-04: Should write, read with line numbers, and perform unique edit replacement")
    void should_performWriteReadAndEditSuccessfully() throws Exception {
        ToolContext ctx1 = createContext("call-1");
        ToolContext ctx2 = createContext("call-2");
        ToolContext ctx3 = createContext("call-3");

        // 1. 写入文件
        String initialContent = "line 1: hello\nline 2: target to change\nline 3: world\n";
        ToolResult writeRes = writeTool.execute(ctx1, Map.of(
                "path", "src/Hello.java",
                "content", initialContent
        )).get();
        assertThat(writeRes.isError()).isFalse();
        assertThat(writeRes.output()).contains("Successfully wrote");

        // 2. 读取带行号的文件
        ToolResult readRes = readTool.execute(ctx2, Map.of(
                "path", "src/Hello.java",
                "offset", 1,
                "limit", 10
        )).get();
        assertThat(readRes.isError()).isFalse();
        assertThat(readRes.output()).contains("   1: line 1: hello");
        assertThat(readRes.output()).contains("   2: line 2: target to change");

        // 3. 编辑文件（唯一替换）
        ToolResult editRes = editTool.execute(ctx3, Map.of(
                "path", "src/Hello.java",
                "old_string", "line 2: target to change",
                "new_string", "line 2: successfully replaced"
        )).get();
        assertThat(editRes.isError()).isFalse();
        assertThat(editRes.output()).contains("Successfully replaced 1 occurrence");

        // 4. 校验更新后的文件内容
        ToolResult readUpdated = readTool.execute(createContext("call-4"), Map.of("path", "src/Hello.java")).get();
        assertThat(readUpdated.output()).contains("line 2: successfully replaced");

        // 5. 校验 replace_all=false 时非唯一替换失败
        Files.writeString(tempWorkspace.resolve("duplicate.txt"), "duplicate\nduplicate\n");
        ToolResult failedEdit = editTool.execute(createContext("call-5"), Map.of(
                "path", "duplicate.txt",
                "old_string", "duplicate",
                "new_string", "replaced"
        )).get();
        assertThat(failedEdit.isError()).isTrue();
        assertThat(failedEdit.output()).contains("Found 2 occurrences");
    }

    /**
     * TC-P2-05: Grep 与 Glob 过滤（跳过 VCS、行号、截断）。
     */
    @Test
    @DisplayName("TC-P2-05: Grep and Glob should filter files and match patterns accurately")
    void should_grepAndGlobFiles() throws Exception {
        // 创建文件结构
        Path srcDir = tempWorkspace.resolve("src/main/java");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("App.java"), "public class App { static int counter = 42; }\n");
        Files.writeString(srcDir.resolve("Service.java"), "public class Service { int counter = 99; }\n");

        // 创建被忽略的目录
        Path gitDir = tempWorkspace.resolve(".git");
        Files.createDirectories(gitDir);
        Files.writeString(gitDir.resolve("HEAD.java"), "int counter = 0;\n");

        // 测试 Glob 工具
        ToolResult globRes = globTool.execute(createContext("glob-1"), Map.of("pattern", "**/*.java")).get();
        assertThat(globRes.isError()).isFalse();
        assertThat(globRes.output()).contains("App.java");
        assertThat(globRes.output()).contains("Service.java");
        assertThat(globRes.output()).doesNotContain("HEAD.java"); // 忽略 .git 目录

        // 测试 Grep 工具
        ToolResult grepRes = grepTool.execute(createContext("grep-1"), Map.of(
                "pattern", "counter",
                "path", "."
        )).get();
        assertThat(grepRes.isError()).isFalse();
        assertThat(grepRes.output()).contains("Found 2 matches");
        assertThat(grepRes.output()).contains("App.java:");
        assertThat(grepRes.output()).contains("Line 1: public class App { static int counter = 42; }");
        assertThat(grepRes.output()).doesNotContain("HEAD.java");
    }

    /**
     * TC-P2-06: Bash 命令执行、超时与退出码报告。
     */
    @Test
    @DisplayName("TC-P2-06: BashTool should execute shell commands, report exit codes, and enforce timeout")
    void should_executeBashAndHandleErrors() throws Exception {
        // 1. 成功的 bash 命令
        ToolResult successRes = bashTool.execute(createContext("bash-1"), Map.of("command", "echo 'Hello from BloomHarness'")).get();
        assertThat(successRes.isError()).isFalse();
        assertThat(successRes.output().trim()).isEqualTo("Hello from BloomHarness");

        // 2. 退出码非零的命令
        ToolResult failRes = bashTool.execute(createContext("bash-2"), Map.of("command", "exit 42")).get();
        assertThat(failRes.isError()).isTrue();
        assertThat(failRes.output()).contains("[exit code: 42]");

        // 3. 命令超时
        ToolResult timeoutRes = bashTool.execute(createContext("bash-3"), Map.of(
                "command", "sleep 5",
                "timeoutMs", 500
        )).get();
        assertThat(timeoutRes.isError()).isTrue();
        assertThat(timeoutRes.output()).contains("[timed out after 500ms]");
    }
}
