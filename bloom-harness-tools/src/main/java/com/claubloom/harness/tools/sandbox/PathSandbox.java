package com.claubloom.harness.tools.sandbox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * PathSandbox 实现工作区文件系统的安全隔离，严格防止路径遍历（../）越界访问。
 * 深度对齐 pi-agent 的安全沙箱防护体系。
 */
public class PathSandbox {

    private final Path workspaceRoot;
    private final List<Path> allowedRoots;
    private final boolean enabled;

    public PathSandbox(Path workspaceRoot) {
        this(workspaceRoot, List.of(), true);
    }

    public PathSandbox(Path workspaceRoot, List<Path> extraAllowedRoots, boolean enabled) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.allowedRoots = new ArrayList<>();
        this.allowedRoots.add(this.workspaceRoot);
        if (extraAllowedRoots != null) {
            for (Path root : extraAllowedRoots) {
                this.allowedRoots.add(root.toAbsolutePath().normalize());
            }
        }
        this.enabled = enabled;
    }

    /**
     * 基于当前会话工作目录（或默认工作区根目录）解析路径，并严格校验其未逃逸出沙箱允许边界。
     *
     * @param rawPath 用户提供的相对或绝对文件路径
     * @param currentWorkingDirectory 当前回合会话工作目录（若未指定则默认为工作区根目录）
     * @return 规范化且在合法沙箱范围内的绝对路径
     * @throws SecurityException 当路径试图逃逸出允许的工作区根目录时抛出安全异常
     */
    public Path resolve(String rawPath, String currentWorkingDirectory) {
        Path baseRoot = (currentWorkingDirectory != null && !currentWorkingDirectory.isBlank())
                ? Paths.get(currentWorkingDirectory.trim()).toAbsolutePath().normalize()
                : workspaceRoot;

        if (rawPath == null || rawPath.isBlank()) {
            return baseRoot;
        }

        // Clean user input: trim Unicode spaces and leading @ if any (mirrors pi's normalizePath)
        String cleanPath = rawPath.trim();
        if (cleanPath.startsWith("@")) {
            cleanPath = cleanPath.substring(1).trim();
        }

        Path target = Paths.get(cleanPath);
        Path resolved;
        if (target.isAbsolute()) {
            resolved = target.normalize();
        } else {
            resolved = baseRoot.resolve(target).normalize();
        }

        if (!enabled) {
            return resolved;
        }

        boolean withinAllowed = false;
        if (resolved.startsWith(baseRoot)) {
            withinAllowed = true;
        } else {
            for (Path root : allowedRoots) {
                if (resolved.startsWith(root)) {
                    withinAllowed = true;
                    break;
                }
            }
        }

        if (!withinAllowed) {
            throw new SecurityException(String.format(
                    "Path sandbox violation: '%s' resolves to '%s', which is outside allowed workspace root '%s'",
                    rawPath, resolved, baseRoot
            ));
        }

        return resolved;
    }

    /**
     * 基于工作区根目录解析路径，并严格校验其未逃逸出沙箱允许边界。
     *
     * @param rawPath 用户提供的相对或绝对文件路径
     * @return 规范化且在合法沙箱范围内的绝对路径
     * @throws SecurityException 当路径试图逃逸出允许的工作区根目录时抛出安全异常
     */
    public Path resolve(String rawPath) {
        return resolve(rawPath, null);
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
