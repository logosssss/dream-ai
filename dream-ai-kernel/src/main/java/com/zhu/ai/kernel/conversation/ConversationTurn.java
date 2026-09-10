package com.zhu.ai.kernel.conversation;

/**
 * 会话中的一条。由 {@link ConversationPort} 按 sessionId 存取，不进 HTTP JSON。
 */
public record ConversationTurn(ConversationRole role, String content) {

    public ConversationTurn {
        if (role == null) {
            throw new IllegalArgumentException("role required");
        }
    }
}
