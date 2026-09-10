package com.zhu.ai.kernel.runtime;

import com.zhu.ai.kernel.observe.InvokeObservation;

/**
 * Gateway 出参。HTTP JSON 用 app 的 {@code AgentInvokeHttpResponse} 映射本类型。
 * {@code agentId} 是实际执行的 Handler，空 id 请求会回落到默认 Agent。
 * {@code observe} 由 Gateway 填入；Agent 只构造 {@link #AgentInvokeResult(String, String)}。
 */
public record AgentInvokeResult(String agentId, String output, InvokeObservation observe) {

    public AgentInvokeResult(String agentId, String output) {
        this(agentId, output, null);
    }
}
