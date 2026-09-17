package com.claubloom.harness.server.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claubloom.harness.protocol.result.ProtocolErrorCode;
import com.claubloom.harness.server.errors.SessionBusyError;
import com.claubloom.harness.server.errors.SessionLockedError;
import com.claubloom.harness.server.errors.SessionNotFoundError;
import com.claubloom.harness.server.sessions.LiveSessionManager;
import com.claubloom.harness.server.service.CreateSessionOptions;
import com.claubloom.harness.server.service.PiServerService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * REST 层错误映射回归测试：业务错误必须映射为语义等价的 HTTP 状态码，
 * 而不是被 Spring 默认压成 500。
 * <p>
 * 修复前 REST 层没有任何全局异常处理，且 {@code SessionController} 把用户输入直接喂给
 * {@code ThinkingLevel.valueOf()}：会话不存在、会话忙、非法思考级别全部返回 500，
 * 客户端无法区分"资源不存在"与"服务端内部错误"。用例 1-3 在修复前失败。
 */
public class RestErrorMappingTest {

    /* ---------- 测试脚手架 ---------- */

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        PiServerService service = mock(PiServerService.class);
        LiveSessionManager liveSessionManager = mock(LiveSessionManager.class);

        when(service.openSession(eq("nope")))
                .thenReturn(CompletableFuture.failedFuture(new SessionNotFoundError("Session not found: nope")));
        when(service.openSession(eq("busy")))
                .thenReturn(CompletableFuture.failedFuture(new SessionBusyError("Session is busy")));
        when(service.openSession(eq("locked")))
                .thenReturn(CompletableFuture.failedFuture(new SessionLockedError("Session is locked")));
        when(service.createSession(any(CreateSessionOptions.class)))
                .thenReturn(CompletableFuture.failedFuture(new SessionNotFoundError("stub: 未配置会话存储")));
        when(liveSessionManager.listMetadata()).thenReturn(CompletableFuture.completedFuture(List.of()));

        mvc = MockMvcBuilders
                .standaloneSetup(new SessionController(service, liveSessionManager))
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    /** 控制器返回 {@link CompletableFuture}，须经异步分派才能拿到最终状态码。 */
    private MvcResult awaitAsync(MvcResult initial) throws Exception {
        return mvc.perform(asyncDispatch(initial)).andReturn();
    }

    /* ---------- 用例 ---------- */

    @Test
    @DisplayName("会话不存在：返回 404 而非 500，并携带 not_found 错误码")
    void should_returnNotFound_when_sessionMissing() throws Exception {
        MvcResult initial = mvc.perform(get("/api/sessions/nope"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult result = awaitAsync(initial);

        assertThat(result.getResponse().getStatus()).as("会话不存在应返回 404").isEqualTo(404);
        assertThat(result.getResponse().getContentAsString())
                .as("错误码应为 not_found")
                .contains(ProtocolErrorCode.NOT_FOUND.getValue());
    }

    @Test
    @DisplayName("会话忙：返回 409 而非 500，并携带 busy 错误码")
    void should_returnConflict_when_sessionBusy() throws Exception {
        MvcResult initial = mvc.perform(get("/api/sessions/busy"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult result = awaitAsync(initial);

        assertThat(result.getResponse().getStatus()).as("会话忙应返回 409").isEqualTo(409);
        assertThat(result.getResponse().getContentAsString())
                .as("错误码应为 busy")
                .contains(ProtocolErrorCode.BUSY.getValue());
    }

    @Test
    @DisplayName("会话被锁：返回 423 而非 500，并携带 session_locked 错误码")
    void should_returnLocked_when_sessionLocked() throws Exception {
        MvcResult initial = mvc.perform(get("/api/sessions/locked"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult result = awaitAsync(initial);

        assertThat(result.getResponse().getStatus()).as("会话被锁应返回 423").isEqualTo(423);
        assertThat(result.getResponse().getContentAsString())
                .as("错误码应为 session_locked")
                .contains(ProtocolErrorCode.SESSION_LOCKED.getValue());
    }

    @Test
    @DisplayName("思考级别非法：返回 400 而非抛出异常，且提示可用的取值")
    void should_returnBadRequest_when_thinkingLevelInvalid() throws Exception {
        MvcResult result = mvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwd\":\"/tmp\",\"thinkingLevel\":\"banana\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).as("非法思考级别应返回 400").isEqualTo(400);
        assertThat(result.getResponse().getContentAsString())
                .as("错误码应为 invalid_request")
                .contains(ProtocolErrorCode.INVALID_REQUEST.getValue());
        assertThat(result.getResponse().getContentAsString())
                .as("错误信息应包含非法取值与可用取值，便于用户纠正")
                .contains("banana")
                .contains("high");
    }

    @Test
    @DisplayName("思考级别合法：校验放行，请求正常进入业务逻辑")
    void should_acceptRequest_when_thinkingLevelValid() throws Exception {
        MvcResult initial = mvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwd\":\"/tmp\",\"thinkingLevel\":\"high\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult result = awaitAsync(initial);

        // 桩服务在 createSession 阶段返回 not_found，说明思考级别校验已放行、流程走到了业务层
        assertThat(result.getResponse().getStatus())
                .as("合法思考级别不应被参数校验拦截（应进入业务逻辑而非 400）")
                .isEqualTo(404);
    }
}
