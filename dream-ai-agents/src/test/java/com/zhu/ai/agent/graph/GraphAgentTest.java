package com.zhu.ai.agent.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.graph.GraphPort;
import com.zhu.ai.kernel.graph.GraphRunResult;
import com.zhu.ai.kernel.llm.TokenSink;
import com.zhu.ai.kernel.observe.InvokeObservation;
import com.zhu.ai.kernel.runtime.AgentInvokeRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphAgentTest {

    @Test
    void handleStreamEmitsRoutePrefixThenLeafDeltas() {
        GraphPort graph = request -> {
            TokenSink sink = request.sink();
            if (sink != null) {
                sink.onRoute("review");
                sink.onModel("qwen-max");
                sink.onDelta("风险");
                sink.onDelta("：缺边界检查");
            }
            return new GraphRunResult("review", "风险：缺边界检查", "qwen-max");
        };
        RecordingObserve observe = new RecordingObserve();
        GraphAgent agent = new GraphAgent(graph, observe);

        List<String> routes = new ArrayList<>();
        List<String> models = new ArrayList<>();
        List<String> deltas = new ArrayList<>();
        TokenSink sink = new TokenSink() {
            @Override
            public void onDelta(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onRoute(String route) {
                routes.add(route);
            }

            @Override
            public void onModel(String model) {
                models.add(model);
            }
        };

        var result = agent.handleStream(
                new AgentInvokeRequest("graph", "s1", "请审查这段代码有没有风险点"), sink);

        assertEquals(List.of("review"), routes);
        assertEquals(List.of("qwen-max"), models);
        assertEquals(List.of("[route=review]\n", "风险", "：缺边界检查"), deltas);
        assertTrue(result.output().contains("风险"));
        assertEquals("review", observe.lastRoute);
    }

    /** 最小观测桩：只记 markRoute，避免 agents 测试依赖 app 的 InMemoryObservePort。 */
    private static final class RecordingObserve implements com.zhu.ai.kernel.observe.ObservePort {
        private String lastRoute = "";

        @Override
        public String begin(String sessionId, String agentId) {
            return "t";
        }

        @Override
        public void markModelCall() {}

        @Override
        public void markToolCalls(int count) {}

        @Override
        public void markToolExecuted(String toolName) {}

        @Override
        public void markRoute(String route) {
            lastRoute = route == null ? "" : route;
        }

        @Override
        public void markModel(String model) {}

        @Override
        public void markToolBlocked(String toolName) {}

        @Override
        public InvokeObservation complete(boolean success, String errorMessage) {
            return new InvokeObservation(
                    "t", "s", "graph", 0, 0, 0, success, errorMessage, lastRoute, "", List.of(), List.of());
        }

        @Override
        public List<InvokeObservation> recent(int limit) {
            return List.of();
        }
    }
}
