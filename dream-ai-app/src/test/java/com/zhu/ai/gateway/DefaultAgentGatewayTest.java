package com.zhu.ai.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.conversation.InMemoryConversationPort;
import com.zhu.ai.knowledge.InMemoryKeywordIndex;
import com.zhu.ai.kernel.agent.AgentHandler;
import com.zhu.ai.kernel.observe.InvokeObservation;
import com.zhu.ai.kernel.observe.ObservePort;
import com.zhu.ai.kernel.runtime.AgentInvokeRequest;
import com.zhu.ai.kernel.runtime.AgentInvokeResult;
import com.zhu.ai.memory.InMemoryMemoryPort;
import com.zhu.ai.observe.InMemoryObservePort;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DefaultAgentGatewayTest {

    @Test
    void sameSessionSeesPriorTurns() {
        AtomicInteger lastHistorySize = new AtomicInteger();
        DefaultAgentGateway gateway = gateway((id, req) -> {
            lastHistorySize.set(req.history().size());
            return new AgentInvokeResult(id, "out:" + req.input());
        });
        gateway.invoke(new AgentInvokeRequest("chat", "s1", "第一轮"));
        assertEquals(0, lastHistorySize.get());
        gateway.invoke(new AgentInvokeRequest("chat", "s1", "第二轮"));
        assertEquals(2, lastHistorySize.get());
    }

    @Test
    void differentSessionsAreIsolated() {
        AtomicInteger lastHistorySize = new AtomicInteger();
        DefaultAgentGateway gateway = gateway((id, req) -> {
            lastHistorySize.set(req.history().size());
            return new AgentInvokeResult(id, "ok");
        });
        gateway.invoke(new AgentInvokeRequest("chat", "a", "hi"));
        gateway.invoke(new AgentInvokeRequest("chat", "b", "hi"));
        assertEquals(0, lastHistorySize.get());
    }

    @Test
    void secondTurnGetsMemoryNotes() {
        AtomicReference<String> lastMemory = new AtomicReference<>();
        DefaultAgentGateway gateway = gateway((id, req) -> {
            lastMemory.set(req.memoryNotes());
            return new AgentInvokeResult(id, "ok");
        });
        gateway.invoke(new AgentInvokeRequest("chat", "mem-1", "我叫小明"));
        assertEquals("", lastMemory.get());
        gateway.invoke(new AgentInvokeRequest("chat", "mem-1", "我叫什么"));
        assertTrue(lastMemory.get().contains("我叫小明"));
        assertTrue(lastMemory.get().contains("ok"));
    }

    @Test
    void retrieveHitsGoToHandler() {
        var index = new InMemoryKeywordIndex();
        index.ingest("HTTP 只进 AgentGateway，不拆 8096。");
        AtomicReference<String> retrieved = new AtomicReference<>();
        ObservePort observe = new InMemoryObservePort();
        DefaultAgentGateway gateway = new DefaultAgentGateway(
                new InMemoryAgentRegistry(List.of(new RecordingHandler("chat", (id, req) -> {
                    retrieved.set(req.retrievedContext());
                    return new AgentInvokeResult(id, "ok");
                }))),
                new InMemoryConversationPort(),
                new InMemoryMemoryPort(),
                index,
                observe);
        gateway.invoke(new AgentInvokeRequest("chat", "rag-1", "什么是 AgentGateway"));
        assertTrue(retrieved.get().startsWith("[1]"));
        assertTrue(retrieved.get().contains("AgentGateway"));
    }

    @Test
    void chitchatDoesNotRetrieve() {
        var index = new InMemoryKeywordIndex();
        index.ingest("HTTP 只进 AgentGateway，不拆 8096。");
        AtomicReference<String> retrieved = new AtomicReference<>("unset");
        DefaultAgentGateway gateway = new DefaultAgentGateway(
                new InMemoryAgentRegistry(List.of(new RecordingHandler("chat", (id, req) -> {
                    retrieved.set(req.retrievedContext());
                    return new AgentInvokeResult(id, "ok");
                }))),
                new InMemoryConversationPort(),
                new InMemoryMemoryPort(),
                index,
                new InMemoryObservePort());
        gateway.invoke(new AgentInvokeRequest("chat", "chat-1", "你好"));
        assertEquals("", retrieved.get());
    }

    @Test
    void observeKeepsRetrieveSummaries() {
        var index = new InMemoryKeywordIndex();
        index.ingest("HTTP 只进 AgentGateway，不拆 8096。");
        ObservePort observe = new InMemoryObservePort();
        DefaultAgentGateway gateway = new DefaultAgentGateway(
                new InMemoryAgentRegistry(List.of(new RecordingHandler("chat", (id, req) ->
                        new AgentInvokeResult(id, "ok")))),
                new InMemoryConversationPort(),
                new InMemoryMemoryPort(),
                index,
                observe);
        AgentInvokeResult result =
                gateway.invoke(new AgentInvokeRequest("chat", "rag-obs", "什么是 AgentGateway"));
        assertFalse(result.observe().retrieveHits().isEmpty());
        assertTrue(result.observe().retrieveHits().getFirst().id().startsWith("kw-"));
        assertTrue(result.observe().retrieveHits().getFirst().score() > 0);
    }

    @Test
    void failedHandleDoesNotAppend() {
        InMemoryConversationPort conversation = new InMemoryConversationPort();
        InMemoryMemoryPort memory = new InMemoryMemoryPort();
        ObservePort observe = new InMemoryObservePort();
        DefaultAgentGateway gateway = new DefaultAgentGateway(
                new InMemoryAgentRegistry(List.of(new RecordingHandler("chat", (id, req) -> {
                    throw new IllegalArgumentException("input required");
                }))),
                conversation,
                memory,
                new InMemoryKeywordIndex(),
                observe);
        assertThrows(
                IllegalArgumentException.class,
                () -> gateway.invoke(new AgentInvokeRequest("chat", "s1", "x")));
        assertTrue(conversation.history("s1").isEmpty());
        assertEquals("", memory.recall("s1"));
        InvokeObservation failed = observe.recent(1).get(0);
        assertFalse(failed.success());
        assertEquals("input required", failed.errorMessage());
    }

    @Test
    void invokeReturnsTraceAndStoresRecent() {
        ObservePort observe = new InMemoryObservePort();
        DefaultAgentGateway gateway = new DefaultAgentGateway(
                new InMemoryAgentRegistry(List.of(new RecordingHandler("chat", (id, req) -> {
                    observe.markModelCall();
                    observe.markToolCalls(2);
                    return new AgentInvokeResult(id, "ok");
                }))),
                new InMemoryConversationPort(),
                new InMemoryMemoryPort(),
                new InMemoryKeywordIndex(),
                observe);
        AgentInvokeResult result = gateway.invoke(new AgentInvokeRequest("chat", "obs-1", "hi"));
        assertNotNull(result.observe());
        assertFalse(result.observe().traceId().isBlank());
        assertEquals(1, result.observe().modelCalls());
        assertEquals(2, result.observe().toolCalls());
        assertTrue(result.observe().success());
        assertEquals(1, observe.recent(10).size());
        assertEquals(result.observe().traceId(), observe.recent(1).get(0).traceId());
    }

    @Test
    void invokeStreamStoresConversationAndPushesDeltas() {
        java.util.List<String> deltas = new java.util.ArrayList<>();
        InMemoryConversationPort conversation = new InMemoryConversationPort();
        DefaultAgentGateway gateway = new DefaultAgentGateway(
                new InMemoryAgentRegistry(List.of(new RecordingHandler("chat", (id, req) ->
                        new AgentInvokeResult(id, "hello")))),
                conversation,
                new InMemoryMemoryPort(),
                new InMemoryKeywordIndex(),
                new InMemoryObservePort());
        AgentInvokeResult result =
                gateway.invokeStream(new AgentInvokeRequest("chat", "sse-1", "hi"), deltas::add);
        assertEquals("hello", result.output());
        assertEquals(List.of("hello"), deltas);
        assertEquals(2, conversation.history("sse-1").size());
    }

    @Test
    void invokeStreamCancelDoesNotAppend() {
        InMemoryConversationPort conversation = new InMemoryConversationPort();
        DefaultAgentGateway gateway = new DefaultAgentGateway(
                new InMemoryAgentRegistry(List.of(new RecordingHandler("chat", (id, req) -> {
                    throw new com.zhu.ai.kernel.llm.StreamCancelledException();
                }))),
                conversation,
                new InMemoryMemoryPort(),
                new InMemoryKeywordIndex(),
                new InMemoryObservePort());
        assertThrows(
                com.zhu.ai.kernel.llm.StreamCancelledException.class,
                () -> gateway.invokeStream(new AgentInvokeRequest("chat", "sse-2", "hi"), delta -> {}));
        assertTrue(conversation.history("sse-2").isEmpty());
    }

    private static DefaultAgentGateway gateway(RecordingHandler.Fn fn) {
        return new DefaultAgentGateway(
                new InMemoryAgentRegistry(List.of(new RecordingHandler("chat", fn))),
                new InMemoryConversationPort(),
                new InMemoryMemoryPort(),
                new InMemoryKeywordIndex(),
                new InMemoryObservePort());
    }

    private static final class RecordingHandler implements AgentHandler {

        @FunctionalInterface
        interface Fn {
            AgentInvokeResult apply(String id, AgentInvokeRequest request);
        }

        private final String id;
        private final Fn fn;

        private RecordingHandler(String id, Fn fn) {
            this.id = id;
            this.fn = fn;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return id;
        }

        @Override
        public AgentInvokeResult handle(AgentInvokeRequest request) {
            return fn.apply(id, request);
        }
    }
}
