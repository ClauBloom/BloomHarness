package com.claubloom.harness.server.protocol;

/**
 * 协议版本常量与校验。
 * 对齐 pi 协议的 PROTOCOL_VERSION / isSupportedProtocolVersion。
 */
public final class ProtocolVersion {

    /** 当前协议版本号（对齐 pi-agent PROTOCOL_VERSION = 1）。 */
    public static final int CURRENT = 1;

    private ProtocolVersion() {
    }

    public static boolean isSupported(int version) {
        return version == CURRENT;
    }
}
