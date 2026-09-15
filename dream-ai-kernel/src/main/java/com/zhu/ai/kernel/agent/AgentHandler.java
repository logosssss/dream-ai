package com.zhu.ai.kernel.agent;

import com.zhu.ai.kernel.llm.TokenSink;
import com.zhu.ai.kernel.runtime.AgentInvokeRequest;
import com.zhu.ai.kernel.runtime.AgentInvokeResult;

/**
 * 业务 Agent 插件（Strategy）。
 * <p>
 * 实现放在 {@code dream-ai-agents} 的包里，不单独开 Maven 模块。
 * 只依赖 kernel 的 Port（如 {@link com.zhu.ai.kernel.llm.ChatPort}），禁止引用 Spring AI / 厂商 SDK。
 * 由 {@link AgentRegistry} 按 {@link #id()} 查找；HTTP 不直接注入本接口。
 */
public interface AgentHandler {

    /** 稳定标识，对应请求里的 {@code agentId}，如 {@code chat}。 */
    String id();

    /** 给人看的名称，不参与路由。 */
    String name();

    AgentInvokeResult handle(AgentInvokeRequest request);

    /**
     * 流式处理。默认先 {@link #handle} 再整段推给 sink（Graph 等未接 stream 的 Agent）。
     * {@code chat} 覆盖为走 {@code ChatPort#stream}。
     */
    default AgentInvokeResult handleStream(AgentInvokeRequest request, TokenSink sink) {
        AgentInvokeResult result = handle(request);
        if (sink != null && result.output() != null && !result.output().isEmpty() && !sink.cancelled()) {
            sink.onDelta(result.output());
        }
        return result;
    }
}
