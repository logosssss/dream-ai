package com.zhu.ai.gateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RetrievePolicyTest {

    @Test
    void knowledgeIntentRetrieves() {
        assertTrue(RetrievePolicy.shouldRetrieve("什么是 AgentGateway"));
        assertTrue(RetrievePolicy.shouldRetrieve("请用知识库回答"));
    }

    @Test
    void chitchatAndReviewSkipRetrieve() {
        assertFalse(RetrievePolicy.shouldRetrieve("你好"));
        assertFalse(RetrievePolicy.shouldRetrieve("请审查这段代码有没有风险点"));
    }
}
