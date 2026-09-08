package com.claubloom.harness.server.protocol;

/**
 * 协议版本常量与兼容性校验。当前仅支持版本 1。
 */
public final class ProtocolVersion {

    /** 当前协议版本号。 */
    public static final int CURRENT = 1;

    private ProtocolVersion() {
    }

    public static boolean isSupported(int version) {
        return version == CURRENT;
    }
}
