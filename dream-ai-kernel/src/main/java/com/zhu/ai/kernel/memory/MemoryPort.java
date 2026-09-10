package com.zhu.ai.kernel.memory;

/**
 * 长期记忆。按 memoryKey（当前用 sessionId）存跨轮笔记；实现放在 app（Redis 或内存）。
 * 由 {@link com.zhu.ai.kernel.runtime.AgentGateway} 编排：invoke 前 recall，成功后 remember。
 * Agent 不注入本端口。与 {@link com.zhu.ai.kernel.conversation.ConversationPort} 的短窗口分开。
 */
public interface MemoryPort {

    String recall(String memoryKey);

    void rememberRound(String memoryKey, String userInput, String assistantOutput);
}
