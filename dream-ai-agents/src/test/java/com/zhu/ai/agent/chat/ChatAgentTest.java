package com.zhu.ai.agent.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.conversation.ConversationRole;
import com.zhu.ai.kernel.conversation.ConversationTurn;
import com.zhu.ai.kernel.runtime.AgentInvokeRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatAgentTest {

    @Test
    void idAndName() {
        ChatAgent agent = new ChatAgent(request -> request.prompt());
        assertEquals("chat", agent.id());
        assertEquals("对话助手", agent.name());
    }

    @Test
    void handleCallsChatPort() {
        ChatAgent agent = new ChatAgent(request -> {
            assertEquals("你好", request.prompt());
            assertTrue(request.system() != null && request.system().contains("对话助手"));
            assertTrue(request.system().contains("web_search"));
            return "收到";
        });
        var result = agent.handle(new AgentInvokeRequest("chat", "s1", "你好"));
        assertEquals("chat", result.agentId());
        assertEquals("收到", result.output());
    }

    @Test
    void handleForwardsHistory() {
        ChatAgent agent = new ChatAgent(request -> {
            assertEquals(2, request.history().size());
            assertEquals("上轮", request.history().get(0).content());
            return "记得";
        });
        var history = List.of(
                new ConversationTurn(ConversationRole.USER, "上轮"),
                new ConversationTurn(ConversationRole.ASSISTANT, "回"));
        var result = agent.handle(new AgentInvokeRequest("chat", "s1", "这轮", history));
        assertEquals("记得", result.output());
    }

    @Test
    void handlePutsMemoryIntoSystem() {
        ChatAgent agent = new ChatAgent(request -> {
            assertTrue(request.system().contains("长期记忆"));
            assertTrue(request.system().contains("我叫小明"));
            return "小明";
        });
        var result = agent.handle(new AgentInvokeRequest("chat", "s1", "我叫什么", List.of(), "U: 我叫小明\nA: 好的\n"));
        assertEquals("小明", result.output());
    }

    @Test
    void handlePutsRetrievedIntoSystem() {
        ChatAgent agent = new ChatAgent(request -> {
            assertTrue(request.system().contains("参考资料"));
            assertTrue(request.system().contains("AgentGateway"));
            return "门面";
        });
        var result = agent.handle(new AgentInvokeRequest(
                "chat", "s1", "Gateway 是什么", List.of(), "", "HTTP 只进 AgentGateway。"));
        assertEquals("门面", result.output());
    }

    @Test
    void handleRejectsBlankInput() {
        ChatAgent agent = new ChatAgent(request -> "");
        assertThrows(
                IllegalArgumentException.class,
                () -> agent.handle(new AgentInvokeRequest("chat", "s1", "  ")));
    }
}
