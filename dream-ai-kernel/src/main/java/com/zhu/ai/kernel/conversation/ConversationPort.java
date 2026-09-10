package com.zhu.ai.kernel.conversation;

import java.util.List;

/**
 * 短会话（多轮消息）。实现放在 app；由 {@link com.zhu.ai.kernel.runtime.AgentGateway} 编排。
 * Agent 不注入本端口。空 {@code sessionId} 视为无会话，不读写。
 * 与 {@link com.zhu.ai.kernel.memory.MemoryPort} 的长期记忆分开。
 */
public interface ConversationPort {

    List<ConversationTurn> history(String sessionId);

    void appendRound(String sessionId, String userInput, String assistantOutput);
}
