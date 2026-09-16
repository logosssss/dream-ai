package com.zhu.ai.kernel.observe;

import java.util.List;

/**
 * 一次 {@code AgentGateway#invoke} 的观测快照。
 * 模型/工具次数由 app 侧循环上报；Gateway 负责 begin/complete。
 *
 * @param route          Graph 路由叶名（如 chat/knowledge/review）；非 Graph 路径为空
 * @param blockedTools   本轮被策略拦截的工具名（白名单拒执 / HITL 未批）
 * @param executedTools  本轮真正执行成功的工具名（与 {@code toolCalls} 对应）
 */
public record InvokeObservation(
        String traceId,
        String sessionId,
        String agentId,
        long durationMs,
        int modelCalls,
        int toolCalls,
        boolean success,
        String errorMessage,
        String route,
        List<String> blockedTools,
        List<String> executedTools) {

    public InvokeObservation {
        route = route == null ? "" : route;
        blockedTools = blockedTools == null ? List.of() : List.copyOf(blockedTools);
        executedTools = executedTools == null ? List.of() : List.copyOf(executedTools);
        errorMessage = errorMessage == null ? "" : errorMessage;
    }
}
