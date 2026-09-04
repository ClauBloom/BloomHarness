package com.claubloom.harness.server.rest;

import com.claubloom.harness.protocol.command.CreateCommand;
import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.SessionMetadata;
import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.claubloom.harness.protocol.session.ThinkingLevel;
import com.claubloom.harness.server.service.CreateSessionOptions;
import com.claubloom.harness.server.service.PiServerService;
import com.claubloom.harness.server.sessions.LiveSessionManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话管理 REST 控制器，为 Web 前端提供会话列表查询、新建、附加与配置更新能力。
 */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final PiServerService service;
    private final LiveSessionManager liveSessions;

    @GetMapping
    public CompletableFuture<List<SessionMetadata>> list() {
        return liveSessions.listMetadata();
    }

    @PostMapping
    public CompletableFuture<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String cwd = (String) body.get("cwd");
        String name = (String) body.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> modelMap = (Map<String, Object>) body.get("model");
        ModelRef model = modelMap == null ? null : new ModelRef(
                (String) modelMap.get("provider"), (String) modelMap.get("id"));
        String thinking = (String) body.get("thinkingLevel");
        ThinkingLevel thinkingLevel = thinking == null ? null : ThinkingLevel.valueOf(thinking.toUpperCase());

        return service.createSession(CreateSessionOptions.builder()
                        .id(java.util.UUID.randomUUID().toString())
                        .cwd(cwd)
                        .name(name)
                        .model(model)
                        .thinkingLevel(thinkingLevel)
                        .build())
                .thenApply(runtime -> Map.of("id", runtime.snapshot().id()));
    }

    @GetMapping("/{sessionId}")
    public CompletableFuture<SessionSnapshot> attach(@PathVariable String sessionId) {
        return service.openSession(sessionId).thenApply(runtime -> runtime.snapshot());
    }

    @PutMapping("/{sessionId}/model")
    public CompletableFuture<Map<String, Object>> updateModel(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body) {
        String provider = (String) body.get("provider");
        String modelId = (String) body.get("id");
        ModelRef modelRef = (provider != null && modelId != null) ? new ModelRef(provider, modelId) : null;
        return service.openSession(sessionId).thenApply(runtime -> {
            runtime.setModel(modelRef);
            return Map.of("ok", true, "sessionId", sessionId, "model", modelRef != null ? modelRef : Map.of());
        });
    }

    @PutMapping("/{sessionId}/cwd")
    public CompletableFuture<Map<String, Object>> updateCwd(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        String newCwd = body.get("cwd");
        return service.openSession(sessionId).thenApply(runtime -> {
            if (newCwd != null && !newCwd.isBlank()) {
                runtime.setCwd(newCwd);
            }
            return Map.of("ok", true, "sessionId", sessionId, "cwd", newCwd != null ? newCwd : "");
        });
    }
}
