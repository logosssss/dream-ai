package com.zhu.ai.gateway;

import com.zhu.ai.graph.IntentRouter;

/**
 * Memory 每轮都 recall；RAG 仅知识意图才 retrieve。
 * 与 Graph {@link IntentRouter} 共用同一把尺子，避免「闲聊也打向量库」和「长期记忆当语料库」。
 */
public final class RetrievePolicy {

    private RetrievePolicy() {}

    public static boolean shouldRetrieve(String input) {
        return IntentRouter.KNOWLEDGE.equals(IntentRouter.decide(input));
    }
}
