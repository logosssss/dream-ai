package com.zhu.ai.agent.graph;

import com.zhu.ai.kernel.agent.AgentHandler;
import com.zhu.ai.kernel.graph.GraphPort;
import com.zhu.ai.kernel.graph.GraphRunRequest;
import com.zhu.ai.kernel.graph.GraphRunResult;
import com.zhu.ai.kernel.knowledge.RetrieveHitSummary;
import com.zhu.ai.kernel.llm.TokenSink;
import com.zhu.ai.kernel.observe.ObservePort;
import com.zhu.ai.kernel.runtime.AgentInvokeRequest;
import com.zhu.ai.kernel.runtime.AgentInvokeResult;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Graph Supervisor 业务入口：HTTP {@code agentId=graph}。
 * <p>
 * 放在 {@code dream-ai-agents}：只依赖 kernel 的 {@link GraphPort} / {@link ObservePort}，
 * 不碰 Alibaba Graph SDK（实现仍在 app 的 SupervisorGraph）。
 * SSE：把 {@link TokenSink} 传入图；路由节点先 {@code onRoute}，叶内推文本；本类只补 {@code [route=]} 前缀。
 */
@Component
public class GraphAgent implements AgentHandler {

    public static final String ID = "graph";

    private final GraphPort graphPort;
    private final ObservePort observe;

    public GraphAgent(GraphPort graphPort, ObservePort observe) {
        this.graphPort = graphPort;
        this.observe = observe;
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
        GraphRunResult result = runGraph(request, null);
        return new AgentInvokeResult(ID, formatOutput(result));
    }

    @Override
    public AgentInvokeResult handleStream(AgentInvokeRequest request, TokenSink sink) {
        GraphRunResult result = runGraph(request, wrapSink(sink));
        return new AgentInvokeResult(ID, formatOutput(result));
    }

    private GraphRunResult runGraph(AgentInvokeRequest request, TokenSink sink) {
        if (request.input() == null || request.input().isBlank()) {
            throw new IllegalArgumentException("input required");
        }
        GraphRunResult result = graphPort.run(new GraphRunRequest(
                request.input(),
                request.history(),
                request.memoryNotes(),
                request.retrievedContext(),
                sink));
        if (observe != null) {
            observe.markRoute(result.route());
        }
        return result;
    }

    /**
     * 在图上报的 {@code onRoute} 后立刻推 {@code [route=…]\n} 前缀，与同步 HTTP 出参对齐；
     * 正文 delta 由叶节点继续推，本类不再整段重推。
     */
    private static TokenSink wrapSink(TokenSink sink) {
        if (sink == null) {
            return null;
        }
        return new TokenSink() {
            private boolean prefixed;

            @Override
            public void onDelta(String delta) {
                sink.onDelta(delta);
            }

            @Override
            public void onRoute(String route) {
                sink.onRoute(route);
                if (!prefixed && route != null && !route.isBlank() && !sink.cancelled()) {
                    sink.onDelta("[route=" + route + "]\n");
                    prefixed = true;
                }
            }

            @Override
            public void onModel(String model) {
                sink.onModel(model);
            }

            @Override
            public void onToolStart(String toolName) {
                sink.onToolStart(toolName);
            }

            @Override
            public void onToolBlocked(String toolName) {
                sink.onToolBlocked(toolName);
            }

            @Override
            public void onToolExecuted(String toolName) {
                sink.onToolExecuted(toolName);
            }

            @Override
            public void onToolFailed(String toolName) {
                sink.onToolFailed(toolName);
            }

            @Override
            public void onRetrieve(List<RetrieveHitSummary> hits) {
                sink.onRetrieve(hits);
            }

            @Override
            public boolean cancelled() {
                return sink.cancelled();
            }
        };
    }

    private static String formatOutput(GraphRunResult result) {
        return "[route=" + result.route() + "]\n" + result.output();
    }
}
