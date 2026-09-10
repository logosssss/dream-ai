package com.zhu.ai.kernel.observe;

/**
 * 一次 {@code AgentGateway#invoke} 的观测快照。
 * 模型/工具次数由 app 侧循环上报；Gateway 负责 begin/complete。
 */
public record InvokeObservation(
        String traceId,
        String sessionId,
        String agentId,
        long durationMs,
        int modelCalls,
        int toolCalls,
        boolean success,
        String errorMessage) {
}
