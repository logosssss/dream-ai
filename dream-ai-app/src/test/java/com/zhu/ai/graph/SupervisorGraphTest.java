package com.zhu.ai.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.llm.ChatRequest;
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

        SupervisorGraph.Result result = graph.run("现在几点了", List.of(), "", "");
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

        SupervisorGraph.Result result =
                graph.run("从知识库检索 AgentGateway", List.of(), "", "HTTP 只进 AgentGateway。");
        assertEquals(IntentRouter.KNOWLEDGE, result.route());
        assertEquals("知识答案", result.output());
        assertTrue(seen.get().system().contains("知识问答助手"));
        assertTrue(seen.get().system().contains("HTTP 只进 AgentGateway"));
    }

    @Test
    void graphAgentPrefixesRoute() {
        SupervisorGraph graph = new SupervisorGraph(request -> "ok");
        GraphAgentHandler handler = new GraphAgentHandler(graph);
        var result = handler.handle(new com.zhu.ai.kernel.runtime.AgentInvokeRequest("graph", "s1", "你好"));
        assertEquals("graph", result.agentId());
        assertTrue(result.output().startsWith("[route=chat]"));
        assertTrue(result.output().contains("ok"));
    }
}
