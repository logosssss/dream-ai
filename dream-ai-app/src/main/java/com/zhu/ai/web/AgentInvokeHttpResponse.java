package com.zhu.ai.web;

import com.zhu.ai.kernel.observe.InvokeObservation;
import com.zhu.ai.kernel.runtime.AgentInvokeResult;

/**
 * HTTP 出参。由 kernel {@link AgentInvokeResult} 映射而来；含本轮观测摘要。
 */
public record AgentInvokeHttpResponse(
        String agentId,
        String output,
        String traceId,
        long durationMs,
        int modelCalls,
        int toolCalls) {

    static AgentInvokeHttpResponse from(AgentInvokeResult result) {
        InvokeObservation observe = result.observe();
        if (observe == null) {
            return new AgentInvokeHttpResponse(result.agentId(), result.output(), "", 0L, 0, 0);
        }
        return new AgentInvokeHttpResponse(
                result.agentId(),
                result.output(),
                observe.traceId(),
                observe.durationMs(),
                observe.modelCalls(),
                observe.toolCalls());
    }
}
