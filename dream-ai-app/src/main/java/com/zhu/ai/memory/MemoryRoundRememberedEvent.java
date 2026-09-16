package com.zhu.ai.memory;

/**
 * Gateway 成功 {@code rememberRound} 后发布；由异步监听器决定是否摘要压缩。
 *
 * @param memoryKey 与 {@link com.zhu.ai.kernel.memory.MemoryPort} 一致，当前为 sessionId
 */
public record MemoryRoundRememberedEvent(String memoryKey) {
}
