package com.zhu.ai.graph;

import com.zhu.ai.kernel.agent.AgentHandler;
import com.zhu.ai.kernel.graph.GraphPort;
import com.zhu.ai.kernel.graph.GraphRunRequest;
import com.zhu.ai.kernel.graph.GraphRunResult;
import com.zhu.ai.kernel.observe.ObservePort;
import com.zhu.ai.kernel.runtime.AgentInvokeRequest;
import com.zhu.ai.kernel.runtime.AgentInvokeResult;

/**
 * Graph Supervisor Agent：HTTP {@code agentId=graph}。
 * <p>
 * 只依赖 {@link GraphPort}，不直接碰 Alibaba Graph SDK。
 * 路由结果写入 {@link ObservePort#markRoute}，便于 {@code /api/observe} 对照。
 */
public class GraphAgentHandler implements AgentHandler {

    public static final String ID = "graph";

    private final GraphPort graphPort;
    private final ObservePort observe;

    public GraphAgentHandler(GraphPort graphPort, ObservePort observe) {
        this.graphPort = graphPort;
        this.observe = observe;
    }

    /** 单测可不接观测。 */
    public GraphAgentHandler(GraphPort graphPort) {
        this(graphPort, null);
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
        GraphRunResult result = graphPort.run(new GraphRunRequest(
                request.input(),
                request.history(),
                request.memoryNotes(),
                request.retrievedContext()));
        if (observe != null) {
            observe.markRoute(result.route());
        }
        String output = "[route=" + result.route() + "]\n" + result.output();
        return new AgentInvokeResult(ID, output);
    }
}
