package com.claubloom.harness.server.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * Workspace directory browsing and validation REST API.
 * Enables the Web UI to:
 * - Query current default workspace and user home directory
 * - Browse host filesystem directories with security validation
 * - Validate arbitrary folder paths entered by users
 * - List recently used workspaces
 */
@Slf4j
@RestController
@RequestMapping("/api/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final com.claubloom.harness.storage.service.SessionStorageService storage;

    /**
     * Get basic environment info for workspaces: default directory, user home, OS.
     */
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentWorkspace() {
        String userDir = System.getProperty("user.dir");
        String userHome = System.getProperty("user.home");
        String osName = System.getProperty("os.name");

        return ResponseEntity.ok(Map.of(
                "defaultWorkspace", userDir,
                "userHome", userHome,
                "os", osName != null ? osName : "unknown",
                "fileSeparator", File.separator
        ));
    }

    /**
     * Browse subdirectories of a given path.
     * If path is empty, defaults to user.dir.
     */
    @GetMapping("/browse")
    public ResponseEntity<Map<String, Object>> browse(@RequestParam(value = "path", required = false) String rawPath) {
        String targetPathStr = (rawPath == null || rawPath.isBlank())
                ? System.getProperty("user.dir")
                : rawPath.trim();

        Path targetPath;
        try {
            if (targetPathStr.startsWith("~")) {
                targetPathStr = System.getProperty("user.home") + targetPathStr.substring(1);
            }
            targetPath = Paths.get(targetPathStr).toAbsolutePath().normalize();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid path format: " + e.getMessage()));
        }

        if (!Files.exists(targetPath)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Path does not exist: " + targetPath));
        }

        if (!Files.isDirectory(targetPath)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Path is not a directory: " + targetPath));
        }

        Path parent = targetPath.getParent();
        List<Map<String, Object>> entries = new ArrayList<>();

        try (Stream<Path> stream = Files.list(targetPath)) {
            stream
                .filter(p -> {
                    try {
                        // Exclude hidden directories starting with '.' unless desired, and ignore inaccessible files
                        String fileName = p.getFileName().toString();
                        if (fileName.startsWith(".") && !fileName.equals(".git")) {
                            return false;
                        }
                        return Files.isDirectory(p);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                .limit(100) // Keep response bounded
                .forEach(dir -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", dir.getFileName().toString());
                    item.put("path", dir.toAbsolutePath().normalize().toString());
                    item.put("isReadable", Files.isReadable(dir));
                    item.put("isWritable", Files.isWritable(dir));
                    entries.add(item);
                });
        } catch (IOException e) {
            log.warn("Failed listing directory {}: {}", targetPath, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed reading directory: " + e.getMessage()));
        }

        return ResponseEntity.ok(Map.of(
                "currentPath", targetPath.toString(),
                "parentPath", parent != null ? parent.toString() : "",
                "directories", entries
        ));
    }

    /**
     * Validate whether a path exists and is a readable directory.
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validatePath(@RequestBody Map<String, String> body) {
        String pathStr = body.get("path");
        if (pathStr == null || pathStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "error", "路径不能为空"));
        }

        try {
            if (pathStr.startsWith("~")) {
                pathStr = System.getProperty("user.home") + pathStr.substring(1);
            }
            Path p = Paths.get(pathStr.trim()).toAbsolutePath().normalize();
            if (!Files.exists(p)) {
                return ResponseEntity.ok(Map.of("valid", false, "error", "目录不存在: " + p));
            }
            if (!Files.isDirectory(p)) {
                return ResponseEntity.ok(Map.of("valid", false, "error", "该路径不是一个有效文件夹: " + p));
            }
            if (!Files.isReadable(p)) {
                return ResponseEntity.ok(Map.of("valid", false, "error", "没有该目录的读取权限"));
            }

            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "canonicalPath", p.toString(),
                    "name", p.getFileName() != null ? p.getFileName().toString() : p.toString(),
                    "isWritable", Files.isWritable(p)
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("valid", false, "error", "路径解析错误: " + e.getMessage()));
        }
    }

    /**
     * Get recent workspace directories from past sessions.
     */
    @GetMapping("/recent")
    public ResponseEntity<List<String>> getRecentWorkspaces() {
        Set<String> uniqueCwds = new LinkedHashSet<>();
        // Current user dir first
        uniqueCwds.add(System.getProperty("user.dir"));

        try {
            var sessions = storage.listSessions();
            for (var s : sessions) {
                if (s.cwd() != null && !s.cwd().isBlank() && Files.isDirectory(Paths.get(s.cwd()))) {
                    uniqueCwds.add(s.cwd());
                }
            }
        } catch (Exception e) {
            log.debug("Error collecting recent sessions cwd: {}", e.getMessage());
        }

        return ResponseEntity.ok(new ArrayList<>(uniqueCwds));
    }

    /**
     * Launch OS native folder selection dialog (Zenity/KDialog on Linux, AppleScript on macOS, PowerShell on Windows).
     */
    @PostMapping("/pick-folder")
    public java.util.concurrent.CompletableFuture<ResponseEntity<Map<String, Object>>> pickFolderDialog(
            @RequestBody(required = false) Map<String, String> body) {
        String initialPath = body != null ? body.get("initialPath") : null;
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            String os = System.getProperty("os.name", "").toLowerCase();
            String chosenPath = null;
            try {
                ProcessBuilder pb = null;
                if (os.contains("linux")) {
                    if (new File("/usr/bin/zenity").exists() || commandExists("zenity")) {
                        List<String> cmd = new ArrayList<>(List.of("zenity", "--file-selection", "--directory", "--title=选择工作区文件夹"));
                        if (initialPath != null && !initialPath.isBlank() && Files.isDirectory(Paths.get(initialPath))) {
                            cmd.add("--filename=" + (initialPath.endsWith(File.separator) ? initialPath : initialPath + File.separator));
                        }
                        pb = new ProcessBuilder(cmd);
                    } else if (new File("/usr/bin/kdialog").exists() || commandExists("kdialog")) {
                        pb = new ProcessBuilder("kdialog", "--getexistingdirectory", initialPath != null ? initialPath : System.getProperty("user.dir"));
                    } else {
                        pb = new ProcessBuilder("python3", "-c", "import tkinter as tk, tkinter.filedialog as fd; root=tk.Tk(); root.withdraw(); print(fd.askdirectory())");
                    }
                } else if (os.contains("mac")) {
                    String script = "POSIX path of (choose folder with prompt \"选择工作区文件夹:\")";
                    pb = new ProcessBuilder("osascript", "-e", script);
                } else if (os.contains("windows")) {
                    String script = "Add-Type -AssemblyName System.Windows.Forms; $f = New-Object System.Windows.Forms.FolderBrowserDialog; if ($f.ShowDialog() -eq 'OK') { Write-Host $f.SelectedPath }";
                    pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", script);
                }

                if (pb != null) {
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    boolean finished = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
                    if (finished && process.exitValue() == 0) {
                        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                            String line = reader.readLine();
                            if (line != null && !line.trim().isBlank()) {
                                chosenPath = line.trim();
                            }
                        }
                    } else if (!finished) {
                        process.destroyForcibly();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed launching native folder dialog: {}", e.getMessage());
            }

            if (chosenPath != null && Files.isDirectory(Paths.get(chosenPath))) {
                return ResponseEntity.ok(Map.of("success", true, "path", chosenPath));
            } else {
                return ResponseEntity.ok(Map.of("success", false, "message", "未选择文件夹或系统对话框已关闭"));
            }
        });
    }

    private boolean commandExists(String cmd) {
        try {
            Process p = new ProcessBuilder("which", cmd).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
