package com.claubloom.harness.tools.truncate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 工具输出的共享截断工具。
 *
 * 截断基于两个相互独立的限制——先命中的限制生效:
 * - 行数限制(默认: 2000 行)
 * - 字节限制(默认: 50KB)
 */
public class OutputTruncator {

    public static final int DEFAULT_MAX_LINES = 2000;
    public static final int DEFAULT_MAX_BYTES = 50 * 1024; // 50KB
    public static final int GREP_MAX_LINE_LENGTH = 500;    // grep 每行匹配结果的最大字符数

    public record TruncationResult(
            String content,
            boolean truncated,
            String truncatedBy, // "lines" | "bytes" | null
            int totalLines,
            int totalBytes,
            int outputLines,
            int outputBytes,
            boolean firstLineExceedsLimit
    ) {}

    /**
     * 将字节数格式化为人类可读的大小字符串（例如 50.0KB）。
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        } else if (bytes < 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1fKB", bytes / 1024.0);
        } else {
            return String.format(java.util.Locale.US, "%.1fMB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * 从头部截断内容（保留前 N 行/字节）。
     * 适用于需要查看文件开头的文件读取场景。
     */
    public static TruncationResult truncateHead(String content) {
        return truncateHead(content, DEFAULT_MAX_LINES, DEFAULT_MAX_BYTES);
    }

    public static TruncationResult truncateHead(String content, int maxLines, int maxBytes) {
        if (content == null || content.isEmpty()) {
            return new TruncationResult("", false, null, 0, 0, 0, 0, false);
        }

        byte[] allBytes = content.getBytes(StandardCharsets.UTF_8);
        int totalBytes = allBytes.length;
        String[] lines = content.split("\n", -1);
        int totalLines = lines.length;

        if (totalLines > 0 && lines[0].getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            return new TruncationResult("", true, "bytes", totalLines, totalBytes, 0, 0, true);
        }

        List<String> outputLinesList = new ArrayList<>();
        int currentBytes = 0;
        boolean truncated = false;
        String truncatedBy = null;

        for (int i = 0; i < lines.length; i++) {
            if (i >= maxLines) {
                truncated = true;
                truncatedBy = "lines";
                break;
            }

            String line = lines[i];
            byte[] lineBytes = (i > 0 ? "\n" + line : line).getBytes(StandardCharsets.UTF_8);
            if (currentBytes + lineBytes.length > maxBytes) {
                truncated = true;
                truncatedBy = "bytes";
                break;
            }

            outputLinesList.add(line);
            currentBytes += lineBytes.length;
        }

        String outputContent = String.join("\n", outputLinesList);
        return new TruncationResult(
                outputContent,
                truncated,
                truncatedBy,
                totalLines,
                totalBytes,
                outputLinesList.size(),
                currentBytes,
                false
        );
    }

    /**
     * 从尾部截断内容（保留最后 N 行/字节）。
     * 适用于 bash 输出场景，可查看结尾部分（错误堆栈、退出码）。
     */
    public static TruncationResult truncateTail(String content) {
        return truncateTail(content, DEFAULT_MAX_LINES, DEFAULT_MAX_BYTES);
    }

    public static TruncationResult truncateTail(String content, int maxLines, int maxBytes) {
        if (content == null || content.isEmpty()) {
            return new TruncationResult("", false, null, 0, 0, 0, 0, false);
        }

        byte[] allBytes = content.getBytes(StandardCharsets.UTF_8);
        int totalBytes = allBytes.length;
        String[] lines = content.split("\n", -1);
        int totalLines = lines.length;

        if (totalLines <= maxLines && totalBytes <= maxBytes) {
            return new TruncationResult(content, false, null, totalLines, totalBytes, totalLines, totalBytes, false);
        }

        List<String> outputLinesList = new ArrayList<>();
        int currentBytes = 0;
        boolean truncated = false;
        String truncatedBy = null;

        for (int i = lines.length - 1; i >= 0; i--) {
            if (outputLinesList.size() >= maxLines) {
                truncated = true;
                truncatedBy = "lines";
                break;
            }

            String line = lines[i];
            byte[] lineBytes = (outputLinesList.isEmpty() ? line : line + "\n").getBytes(StandardCharsets.UTF_8);
            if (currentBytes + lineBytes.length > maxBytes) {
                truncated = true;
                truncatedBy = "bytes";
                break;
            }

            outputLinesList.add(0, line);
            currentBytes += lineBytes.length;
        }

        if (totalBytes > maxBytes && truncatedBy == null) {
            truncated = true;
            truncatedBy = "bytes";
        }

        String outputContent = String.join("\n", outputLinesList);
        return new TruncationResult(
                outputContent,
                true,
                truncatedBy != null ? truncatedBy : "lines",
                totalLines,
                totalBytes,
                outputLinesList.size(),
                currentBytes,
                false
        );
    }
}
