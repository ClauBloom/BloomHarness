package com.claubloom.harness.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务器模块配置属性（bloom-harness.server.*）。
 * 包含服务器标识、帧大小上限与握手超时等协议级参数。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bloom-harness.server")
public class ServerProperties {

    /** 稳定的服务器标识符；为空时自动生成。 */
    private String serverId = "";

    /** 最大数据帧字节长度（默认上限为 16 MiB）。 */
    private Integer maxFrameLength = 16 * 1024 * 1024;

    /** 握手超时时间（毫秒，默认 5000ms）。 */
    private Long handshakeTimeoutMs = 5_000L;
}
