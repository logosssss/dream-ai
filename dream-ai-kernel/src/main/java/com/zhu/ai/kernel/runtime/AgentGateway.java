package com.zhu.ai.kernel.runtime;

import com.zhu.ai.kernel.llm.TokenSink;

/**
 * Agent 运行时门面（Facade）。
 * <p>
 * HTTP / 其它入口只调本接口，不要直连 {@code AgentHandler}。实现放在 app（组合根）：
 * 按 {@code agentId} 分发，并编排短会话、长期记忆与检索。
 */
public interface AgentGateway {

    /**
     * 同步调用一个 Agent。未知 {@code agentId} 由实现抛业务异常。
     */
    AgentInvokeResult invoke(AgentInvokeRequest request);

    /**
     * 流式调用：装上下文后走 Handler {@code handleStream}，全文完成后写会话 / Memory。
     */
    AgentInvokeResult invokeStream(AgentInvokeRequest request, TokenSink sink);
}
