package com.zhu.ai.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IntentRouterTest {

    @Test
    void defaultsToChat() {
        assertEquals(IntentRouter.CHAT, IntentRouter.decide("今天天气怎么样"));
        assertEquals(IntentRouter.CHAT, IntentRouter.decide(""));
        assertEquals(IntentRouter.CHAT, IntentRouter.decide(null));
    }

    @Test
    void routesKnowledgeKeywords() {
        assertEquals(IntentRouter.KNOWLEDGE, IntentRouter.decide("从知识库检索 AgentGateway"));
        assertEquals(IntentRouter.KNOWLEDGE, IntentRouter.decide("RAG 是什么"));
        assertEquals(IntentRouter.KNOWLEDGE, IntentRouter.decide("参考资料里 Gateway 怎么说"));
    }
}
