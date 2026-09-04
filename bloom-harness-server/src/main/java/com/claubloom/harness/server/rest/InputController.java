package com.claubloom.harness.server.rest;

import com.claubloom.harness.server.service.PiServerService;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户输入交互 REST 控制器。
 * 为 Web 前端提供对齐 pi-agent prompt 指令的 HTTP 接口。
 */
@RestController
@RequestMapping("/api/input")
@RequiredArgsConstructor
public class InputController {

    private final PiServerService service;

    @PostMapping("/{sessionId}")
    public CompletableFuture<Map<String, Object>> submit(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        return service.openSession(sessionId)
                .thenCompose(runtime -> CompletableFuture.runAsync(() -> runtime.prompt(text))
                        .thenApply(v -> Map.<String, Object>of("ok", true)));
    }

    @PostMapping("/{sessionId}/abort")
    public CompletableFuture<Map<String, Object>> abort(@PathVariable String sessionId) {
        return service.openSession(sessionId)
                .thenCompose(runtime -> CompletableFuture.runAsync(runtime::abort)
                        .thenApply(v -> Map.<String, Object>of("ok", true, "phase", runtime.snapshot().phase().name().toLowerCase())));
    }

    @PostMapping("/{sessionId}/steer")
    public CompletableFuture<Map<String, Object>> steer(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        return service.openSession(sessionId)
                .thenCompose(runtime -> CompletableFuture.runAsync(() -> runtime.steer(text))
                        .thenApply(v -> Map.<String, Object>of("ok", true)));
    }
}
