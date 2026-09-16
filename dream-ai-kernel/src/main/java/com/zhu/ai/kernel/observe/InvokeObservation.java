package com.zhu.ai.kernel.observe;

import com.zhu.ai.kernel.knowledge.RetrieveHitSummary;
import java.util.List;

/**
 * 一次 {@code AgentGateway#invoke} 的观测快照。
 * 模型/工具次数由 app 侧循环上报；Gateway 负责 begin/complete。
 *
 * @param route          Graph 路由叶名（如 chat/knowledge/review）；非 Graph 路径为空
 * @param model          本轮实际选用的聊天模型 id；未上报为空
 * @param blockedTools   本轮被策略拦截的工具名（白名单拒执 / HITL 未批）
 * @param executedTools  本轮真正执行成功的工具名（与 {@code toolCalls} 对应）
 * @param retrieveHits   本轮检索摘要（id/score/source，无正文）；与 prompt {@code [1]} 顺序一致
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
        String model,
        List<String> blockedTools,
        List<String> executedTools,
        List<RetrieveHitSummary> retrieveHits) {

    public InvokeObservation {
        route = route == null ? "" : route;
        model = model == null ? "" : model;
        blockedTools = blockedTools == null ? List.of() : List.copyOf(blockedTools);
        executedTools = executedTools == null ? List.of() : List.copyOf(executedTools);
        retrieveHits = retrieveHits == null ? List.of() : List.copyOf(retrieveHits);
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    /** 无检索摘要时的便捷构造。 */
    public InvokeObservation(
            String traceId,
            String sessionId,
            String agentId,
            long durationMs,
            int modelCalls,
            int toolCalls,
            boolean success,
            String errorMessage,
            String route,
            String model,
            List<String> blockedTools,
            List<String> executedTools) {
        this(
                traceId,
                sessionId,
                agentId,
                durationMs,
                modelCalls,
                toolCalls,
                success,
                errorMessage,
                route,
                model,
                blockedTools,
                executedTools,
                List.of());
    }
}
