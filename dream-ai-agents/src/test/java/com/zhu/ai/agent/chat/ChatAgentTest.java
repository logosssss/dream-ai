package com.zhu.ai.agent.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.conversation.ConversationRole;
import com.zhu.ai.kernel.conversation.ConversationTurn;
import com.zhu.ai.kernel.llm.ChatPort;
import com.zhu.ai.kernel.llm.ChatRequest;
import com.zhu.ai.kernel.llm.ModelRouter;
import com.zhu.ai.kernel.llm.TokenSink;
import com.zhu.ai.kernel.observe.InvokeObservation;
import com.zhu.ai.kernel.observe.ObservePort;
import com.zhu.ai.kernel.runtime.AgentInvokeRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChatAgentTest {

    private static final ModelRouter NO_MODEL = task -> null;

    @Test
    void idAndName() {
        ChatAgent agent = new ChatAgent(request -> request.prompt(), NO_MODEL, null);
        assertEquals("chat", agent.id());
        assertEquals("对话助手", agent.name());
    }

    @Test
    void handleCallsChatPort() {
        ChatAgent agent = new ChatAgent(
                request -> {
                    assertEquals("你好", request.prompt());
                    assertTrue(request.system() != null && request.system().contains("对话助手"));
                    assertTrue(request.system().contains("web_search"));
                    assertTrue(request.system().contains("datetime_offset"));
                    assertTrue(request.system().contains("text_stats"));
                    return "收到";
                },
                NO_MODEL,
                null);
        var result = agent.handle(new AgentInvokeRequest("chat", "s1", "你好"));
        assertEquals("chat", result.agentId());
        assertEquals("收到", result.output());
    }

    @Test
    void handleStreamUsesChatPortStream() {
        ChatAgent agent = new ChatAgent(
                new ChatPort() {
                    @Override
                    public String complete(ChatRequest request) {
                        throw new AssertionError("stream path must not call complete");
                    }

                    @Override
                    public String stream(ChatRequest request, TokenSink sink) {
                        sink.onDelta("你");
                        sink.onDelta("好");
                        return "你好";
                    }
                },
                NO_MODEL,
                null);
        List<String> deltas = new ArrayList<>();
        var result = agent.handleStream(new AgentInvokeRequest("chat", "s1", "hi"), deltas::add);
        assertEquals(List.of("你", "好"), deltas);
        assertEquals("你好", result.output());
    }

    @Test
    void handleForwardsHistory() {
        ChatAgent agent = new ChatAgent(
                request -> {
                    assertEquals(2, request.history().size());
                    assertEquals("上轮", request.history().get(0).content());
                    return "记得";
                },
                NO_MODEL,
                null);
        var history = List.of(
                new ConversationTurn(ConversationRole.USER, "上轮"),
                new ConversationTurn(ConversationRole.ASSISTANT, "回"));
        var result = agent.handle(new AgentInvokeRequest("chat", "s1", "这轮", history));
        assertEquals("记得", result.output());
    }

    @Test
    void handlePutsMemoryIntoSystem() {
        ChatAgent agent = new ChatAgent(
                request -> {
                    assertTrue(request.system().contains("长期记忆"));
                    assertTrue(request.system().contains("我叫小明"));
                    return "小明";
                },
                NO_MODEL,
                null);
        var result = agent.handle(new AgentInvokeRequest("chat", "s1", "我叫什么", List.of(), "U: 我叫小明\nA: 好的\n"));
        assertEquals("小明", result.output());
    }

    @Test
    void handlePutsRetrievedIntoSystem() {
        ChatAgent agent = new ChatAgent(
                request -> {
                    assertTrue(request.system().contains("参考资料"));
                    assertTrue(request.system().contains("AgentGateway"));
                    return "门面";
                },
                NO_MODEL,
                null);
        var result = agent.handle(new AgentInvokeRequest(
                "chat", "s1", "Gateway 是什么", List.of(), "", "HTTP 只进 AgentGateway。"));
        assertEquals("门面", result.output());
    }

    @Test
    void handleRejectsBlankInput() {
        ChatAgent agent = new ChatAgent(request -> "", NO_MODEL, null);
        assertThrows(
                IllegalArgumentException.class,
                () -> agent.handle(new AgentInvokeRequest("chat", "s1", "  ")));
    }

    @Test
    void handleUsesModelRouterForChatTask() {
        AtomicReference<String> marked = new AtomicReference<>();
        ObservePort observe = new ObservePort() {
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
            public void markRoute(String route) {}

            @Override
            public void markModel(String model) {
                marked.set(model);
            }

            @Override
            public void markToolBlocked(String toolName) {}

            @Override
            public InvokeObservation complete(boolean success, String errorMessage) {
                return new InvokeObservation(
                        "t", "s", "chat", 0, 0, 0, success, errorMessage, "", marked.get(), List.of(), List.of());
            }

            @Override
            public List<InvokeObservation> recent(int limit) {
                return List.of();
            }
        };
        ChatAgent agent = new ChatAgent(
                request -> {
                    assertEquals("qwen-turbo", request.model());
                    return "ok";
                },
                task -> ModelRouter.CHAT.equals(task) ? "qwen-turbo" : null,
                observe);
        assertEquals("ok", agent.handle(new AgentInvokeRequest("chat", "s1", "你好")).output());
        assertEquals("qwen-turbo", marked.get());
    }
}
