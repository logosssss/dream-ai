package com.zhu.ai.kernel.llm;

/**
 * 流式补全的增量出口。HTTP SSE 适配器实现本接口；Agent / ChatPort 不依赖 Servlet。
 */
public interface TokenSink {

    /** 一段模型增量文本；空串可忽略。 */
    void onDelta(String delta);

    /** 客户端断开或超时后为 true；ChatPort 应尽快停。 */
    default boolean cancelled() {
        return false;
    }
}
