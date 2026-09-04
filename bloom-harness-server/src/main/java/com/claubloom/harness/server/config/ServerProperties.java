package com.claubloom.harness.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Server module configuration properties (bloom-harness.server.*).
 * Mirrors pi's PiServerOptions (maxFrameLength, handshakeTimeoutMs, serverId).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bloom-harness.server")
public class ServerProperties {

    /** Stable server identifier; generated when empty. */
    private String serverId = "";

    /** 最大数据帧字节长度（默认上限为 16 MiB）。 */
    private Integer maxFrameLength = 16 * 1024 * 1024;

    /** 握手超时时间（毫秒，默认 5000ms）。 */
    private Long handshakeTimeoutMs = 5_000L;
}
