package com.zhu.ai.conversation;

import com.zhu.ai.kernel.conversation.ConversationPort;
import com.zhu.ai.kernel.conversation.ConversationRole;
import com.zhu.ai.kernel.conversation.ConversationTurn;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内短会话。空 sessionId 不读写；超出 {@link #MAX_TURNS} 丢掉最旧的轮次。
 * 单测和尚未装上 Mapper 时用；长期记忆仍走 {@code MemoryPort}。
 */
public final class InMemoryConversationPort implements ConversationPort {

    public static final int MAX_TURNS = 20;

    private final ConcurrentHashMap<String, List<ConversationTurn>> store = new ConcurrentHashMap<>();

    @Override
    public List<ConversationTurn> history(String sessionId) {
        if (blank(sessionId)) {
            return List.of();
        }
        List<ConversationTurn> turns = store.get(sessionId);
        return turns == null ? List.of() : List.copyOf(turns);
    }

    @Override
    public void appendRound(String sessionId, String userInput, String assistantOutput) {
        if (blank(sessionId)) {
            return;
        }
        store.compute(sessionId, (id, existing) -> {
            List<ConversationTurn> next = new ArrayList<>(existing == null ? List.of() : existing);
            next.add(new ConversationTurn(ConversationRole.USER, userInput));
            next.add(new ConversationTurn(ConversationRole.ASSISTANT, assistantOutput));
            if (next.size() > MAX_TURNS) {
                next = new ArrayList<>(next.subList(next.size() - MAX_TURNS, next.size()));
            }
            return List.copyOf(next);
        });
    }

    private static boolean blank(String sessionId) {
        return sessionId == null || sessionId.isBlank();
    }
}
