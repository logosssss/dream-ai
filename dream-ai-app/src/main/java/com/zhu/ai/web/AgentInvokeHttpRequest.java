package com.zhu.ai.web;

import com.zhu.ai.kernel.runtime.AgentInvokeRequest;

/**
 * HTTP 入参。JSON 只有 agentId / sessionId / input；history / memoryNotes / retrievedContext 由 Gateway 填入。
 */
public record AgentInvokeHttpRequest(String agentId, String sessionId, String input) {

    AgentInvokeRequest toKernel() {
        return new AgentInvokeRequest(agentId, sessionId, input);
    }
}
