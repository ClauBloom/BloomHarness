package com.claubloom.harness.server.connection;

/**
 * 传输层接收到数据/关闭/错误事件时的回调处理器。
 */
public interface ByteConnectionHandler {

    void onData(byte[] chunk);

    void onClose();

    void onError(Throwable error);
}
