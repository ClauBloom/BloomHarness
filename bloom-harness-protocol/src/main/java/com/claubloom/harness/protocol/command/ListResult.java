package com.claubloom.harness.protocol.command;

import com.claubloom.harness.protocol.session.SessionMetadata;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 会话列表查询命令的执行结果。
 */
public record ListResult(
        @JsonProperty(value = "command", defaultValue = "list")
        String command,
        @JsonProperty(value = "sessions", required = true)
        List<SessionMetadata> sessions
) implements CommandResult {
    public ListResult(List<SessionMetadata> sessions) {
        this("list", sessions);
    }
}
