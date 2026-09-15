package com.zhu.ai.graph;

import com.zhu.ai.kernel.agent.AgentHandler;
import com.zhu.ai.kernel.llm.ChatPort;
import com.zhu.ai.kernel.runtime.AgentInvokeRequest;
import com.zhu.ai.kernel.runtime.AgentInvokeResult;

/**
 * Graph Supervisor Agent：HTTP {@code agentId=graph}。
 * <p>
 * 由 {@link com.zhu.ai.config.GraphConfig} 在存在 {@link ChatPort} 时注册；
 * 不用类上 {@code @ConditionalOnBean}，避免组件扫描早于测试 Stub 的 ChatPort。
 */
public class GraphAgentHandler implements AgentHandler {

    public static final String ID = "graph";

    private final SupervisorGraph supervisorGraph;

    public GraphAgentHandler(ChatPort chatPort) {
        this.supervisorGraph = new SupervisorGraph(chatPort);
    }

    /** 测试可注入已构造的图。 */
    GraphAgentHandler(SupervisorGraph supervisorGraph) {
        this.supervisorGraph = supervisorGraph;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Supervisor 编排";
    }

    @Override
    public AgentInvokeResult handle(AgentInvokeRequest request) {
        if (request.input() == null || request.input().isBlank()) {
            throw new IllegalArgumentException("input required");
        }
        SupervisorGraph.Result result = supervisorGraph.run(
                request.input(),
                request.history(),
                request.memoryNotes(),
                request.retrievedContext());
        // 输出首行标注 route，方便演示与面试对照「路由结果」
        String output = "[route=" + result.route() + "]\n" + result.output();
        return new AgentInvokeResult(ID, output);
    }
}
