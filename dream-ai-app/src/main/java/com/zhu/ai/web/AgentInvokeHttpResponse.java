package com.zhu.ai.web;

import com.zhu.ai.kernel.knowledge.RetrieveHitSummary;
import com.zhu.ai.kernel.observe.InvokeObservation;
import com.zhu.ai.kernel.runtime.AgentInvokeResult;
import java.util.List;

/**
 * HTTP 出参。由 kernel {@link AgentInvokeResult} 映射而来；含本轮观测摘要。
 * {@code retrieveHits} 为条数；{@code retrieveHitSummaries} 为 id/score/source，与 prompt {@code [1]} 顺序一致。
 */
public record AgentInvokeHttpResponse(
        String agentId,
        String output,
        String traceId,
        long durationMs,
        int modelCalls,
        int toolCalls,
        String route,
        String model,
        List<String> blockedTools,
        List<String> executedTools,
        List<String> failedTools,
        int retrieveHits,
        List<RetrieveHitSummary> retrieveHitSummaries) {

    static AgentInvokeHttpResponse from(AgentInvokeResult result) {
        InvokeObservation observe = result.observe();
        if (observe == null) {
            return new AgentInvokeHttpResponse(
                    result.agentId(),
                    result.output(),
                    "",
                    0L,
                    0,
                    0,
                    "",
                    "",
                    List.of(),
                    List.of(),
                    List.of(),
                    0,
                    List.of());
        }
        List<RetrieveHitSummary> hits = observe.retrieveHits();
        return new AgentInvokeHttpResponse(
                result.agentId(),
                result.output(),
                observe.traceId(),
                observe.durationMs(),
                observe.modelCalls(),
                observe.toolCalls(),
                observe.route(),
                observe.model(),
                observe.blockedTools(),
                observe.executedTools(),
                observe.failedTools(),
                hits.size(),
                hits);
    }
}
