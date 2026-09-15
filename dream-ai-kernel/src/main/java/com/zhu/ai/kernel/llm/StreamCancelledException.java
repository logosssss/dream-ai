package com.zhu.ai.kernel.llm;

/**
 * 流式调用被取消（客户端断开 / SSE 超时）。Gateway 不再落会话。
 */
public final class StreamCancelledException extends RuntimeException {

    public StreamCancelledException() {
        super("stream cancelled");
    }

    public StreamCancelledException(String message, Throwable cause) {
        super(message, cause);
    }
}
