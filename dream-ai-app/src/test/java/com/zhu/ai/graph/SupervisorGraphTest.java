package com.zhu.ai.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.agent.graph.GraphAgent;
import com.zhu.ai.kernel.graph.GraphPort;
import com.zhu.ai.kernel.graph.GraphRunRequest;
import com.zhu.ai.kernel.graph.GraphRunResult;
import com.zhu.ai.kernel.llm.ChatPort;
import com.zhu.ai.kernel.llm.ChatRequest;
import com.zhu.ai.kernel.llm.ModelRouter;
import com.zhu.ai.kernel.llm.TokenSink;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SupervisorGraphTest {

    @Test
    void chatRouteUsesChatSystem() {
        AtomicReference<ChatRequest> seen = new AtomicReference<>();
        SupervisorGraph graph = new SupervisorGraph(request -> {
            seen.set(request);
            return "chat-ok";
        });

        GraphRunResult result = graph.run("现在几点了", List.of(), "", "");
        assertEquals(IntentRouter.CHAT, result.route());
        assertEquals("chat-ok", result.output());
        assertTrue(seen.get().system().contains("对话助手"));
    }

    @Test
    void knowledgeRouteUsesKnowledgeSystemAndRetrieved() {
        AtomicReference<ChatRequest> seen = new AtomicReference<>();
        SupervisorGraph graph = new SupervisorGraph(request -> {
            seen.set(request);
            return "知识答案";
        });

        GraphRunResult result =
                graph.run("从知识库检索 AgentGateway", List.of(), "", "HTTP 只进 AgentGateway。");
        assertEquals(IntentRouter.KNOWLEDGE, result.route());
        assertEquals("知识答案", result.output());
        assertTrue(seen.get().system().contains("知识问答助手"));
        assertTrue(seen.get().system().contains("HTTP 只进 AgentGateway"));
    }

    @Test
    void knowledgeNoHitRefusesWithoutCallingModel() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        SupervisorGraph graph = new SupervisorGraph(request -> {
            calls.incrementAndGet();
            return "should-not";
        });
        GraphRunResult result = graph.run("从知识库检索 AgentGateway", List.of(), "", "");
        assertEquals(IntentRouter.KNOWLEDGE, result.route());
        assertEquals(SupervisorGraph.NO_HIT_REPLY, result.output());
        assertEquals("", result.model());
        assertEquals(0, calls.get());
    }

    @Test
    void knowledgeHitAppendsCitationWhenModelOmitsIt() {
        SupervisorGraph graph = new SupervisorGraph(request -> "知识答案");
        GraphRunResult result =
                graph.run("从知识库检索 AgentGateway", List.of(), "", "[1] HTTP 只进 AgentGateway。");
        assertEquals(IntentRouter.KNOWLEDGE, result.route());
        assertEquals("知识答案" + SupervisorGraph.CITATION_FALLBACK, result.output());
    }

    @Test
    void knowledgeHitKeepsExistingCitation() {
        SupervisorGraph graph = new SupervisorGraph(request -> "见 [1]");
        GraphRunResult result =
                graph.run("从知识库检索 AgentGateway", List.of(), "", "[1] HTTP 只进 AgentGateway。");
        assertEquals("见 [1]", result.output());
    }

    @Test
    void knowledgeStreamAppendsCitationDelta() {
        ChatPort chat = new ChatPort() {
            @Override
            public String complete(ChatRequest request) {
                return "知识答案";
            }

            @Override
            public String stream(ChatRequest request, TokenSink sink) {
                sink.onDelta("知识答案");
                return "知识答案";
            }
        };
        List<String> deltas = new ArrayList<>();
        TokenSink sink = new TokenSink() {
            @Override
            public void onDelta(String delta) {
                deltas.add(delta);
            }
        };
        SupervisorGraph graph = new SupervisorGraph(chat);
        GraphRunResult result =
                graph.run("从知识库检索 AgentGateway", List.of(), "", "[1] HTTP 只进 AgentGateway。", sink);
        assertEquals("知识答案" + SupervisorGraph.CITATION_FALLBACK, result.output());
        assertEquals(List.of("知识答案", SupervisorGraph.CITATION_FALLBACK), deltas);
    }

    @Test
    void reviewRouteUsesReviewSystem() {
        AtomicReference<ChatRequest> seen = new AtomicReference<>();
        GraphPort graph = new SupervisorGraph(request -> {
            seen.set(request);
            return "review-ok";
        });

        GraphRunResult result = graph.run(new GraphRunRequest("请审查这段代码有没有风险点", List.of(), "", ""));
        assertEquals(IntentRouter.REVIEW, result.route());
        assertEquals("review-ok", result.output());
        assertTrue(seen.get().system().contains("评审助手"));
    }

    @Test
    void graphAgentPrefixesRoute() {
        GraphPort graph = new SupervisorGraph(request -> "ok");
        GraphAgent agent = new GraphAgent(graph, null);
        var result = agent.handle(new com.zhu.ai.kernel.runtime.AgentInvokeRequest("graph", "s1", "你好"));
        assertEquals("graph", result.agentId());
        assertTrue(result.output().startsWith("[route=chat]"));
        assertTrue(result.output().contains("ok"));
    }

    @Test
    void reviewRoutePassesConfiguredModel() {
        AtomicReference<ChatRequest> seen = new AtomicReference<>();
        SupervisorGraph graph = new SupervisorGraph(
                request -> {
                    seen.set(request);
                    return "审毕";
                },
                task -> ModelRouter.REVIEW.equals(task) ? "qwen-max" : null);

        GraphRunResult result = graph.run("请审查这段代码有没有风险点", List.of(), "", "");
        assertEquals(IntentRouter.REVIEW, result.route());
        assertEquals("qwen-max", result.model());
        assertEquals("qwen-max", seen.get().model());
    }

    @Test
    void leafStreamsMultipleDeltasWhenSinkPresent() {
        ChatPort chat = new ChatPort() {
            @Override
            public String complete(ChatRequest request) {
                return "ABCDEF";
            }

            @Override
            public String stream(ChatRequest request, TokenSink sink) {
                sink.onDelta("ABC");
                sink.onDelta("DEF");
                return "ABCDEF";
            }
        };
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

        SupervisorGraph graph = new SupervisorGraph(
                chat, task -> ModelRouter.REVIEW.equals(task) ? "qwen-max" : null);
        GraphRunResult result = graph.run(
                "请审查这段代码有没有风险点", List.of(), "", "", sink);

        assertEquals(IntentRouter.REVIEW, result.route());
        assertEquals("ABCDEF", result.output());
        assertEquals(List.of("review"), routes);
        assertEquals(List.of("qwen-max"), models);
        assertEquals(List.of("ABC", "DEF"), deltas);
    }
}
