package com.zhu.ai.web;

import com.zhu.ai.kernel.runtime.AgentGateway;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 唯一 HTTP 入口。JSON 用 {@link AgentInvokeHttpRequest} / {@link AgentInvokeHttpResponse}，
 * 只调 {@link AgentGateway}，不注入具体 Agent 或 ChatPort。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentInvokeController {

    private final AgentGateway agentGateway;

    public AgentInvokeController(AgentGateway agentGateway) {
        this.agentGateway = agentGateway;
    }

    @PostMapping("/invoke")
    public AgentInvokeHttpResponse invoke(@RequestBody AgentInvokeHttpRequest request) {
        return AgentInvokeHttpResponse.from(agentGateway.invoke(request.toKernel()));
    }
}
