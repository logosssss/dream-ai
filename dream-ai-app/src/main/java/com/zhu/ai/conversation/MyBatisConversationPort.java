package com.zhu.ai.conversation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhu.ai.kernel.conversation.ConversationPort;
import com.zhu.ai.kernel.conversation.ConversationRole;
import com.zhu.ai.kernel.conversation.ConversationTurn;
import com.zhu.ai.persistence.entity.ConversationTurnEntity;
import com.zhu.ai.service.ConversationTurnService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * MySQL 短会话。空 sessionId 不读写；超出 {@link InMemoryConversationPort#MAX_TURNS} 删最旧行。
 */
public class MyBatisConversationPort implements ConversationPort {

    private final ConversationTurnService conversationTurnService;

    public MyBatisConversationPort(ConversationTurnService conversationTurnService) {
        this.conversationTurnService = conversationTurnService;
    }

    @Override
    public List<ConversationTurn> history(String sessionId) {
        if (blank(sessionId)) {
            return List.of();
        }
        List<ConversationTurnEntity> rows = conversationTurnService.list(
                new LambdaQueryWrapper<ConversationTurnEntity>()
                        .eq(ConversationTurnEntity::getSessionId, sessionId)
                        .orderByAsc(ConversationTurnEntity::getId));
        List<ConversationTurn> turns = new ArrayList<>(rows.size());
        for (ConversationTurnEntity row : rows) {
            turns.add(toTurn(row));
        }
        return List.copyOf(turns);
    }

    @Override
    @Transactional
    public void appendRound(String sessionId, String userInput, String assistantOutput) {
        if (blank(sessionId)) {
            return;
        }
        insert(sessionId, ConversationRole.USER, userInput);
        insert(sessionId, ConversationRole.ASSISTANT, assistantOutput);
        trim(sessionId);
    }

    private void insert(String sessionId, ConversationRole role, String content) {
        ConversationTurnEntity row = new ConversationTurnEntity();
        row.setSessionId(sessionId);
        row.setRole(role.name());
        row.setContent(content == null ? "" : content);
        conversationTurnService.save(row);
    }

    private void trim(String sessionId) {
        List<ConversationTurnEntity> newest = conversationTurnService.list(
                new LambdaQueryWrapper<ConversationTurnEntity>()
                        .eq(ConversationTurnEntity::getSessionId, sessionId)
                        .orderByDesc(ConversationTurnEntity::getId)
                        .last("LIMIT " + InMemoryConversationPort.MAX_TURNS));
        if (newest.size() < InMemoryConversationPort.MAX_TURNS) {
            return;
        }
        Long minKeepId = newest.get(newest.size() - 1).getId();
        if (minKeepId == null) {
            return;
        }
        conversationTurnService.remove(new LambdaQueryWrapper<ConversationTurnEntity>()
                .eq(ConversationTurnEntity::getSessionId, sessionId)
                .lt(ConversationTurnEntity::getId, minKeepId));
    }

    private static ConversationTurn toTurn(ConversationTurnEntity row) {
        return new ConversationTurn(ConversationRole.valueOf(row.getRole()), row.getContent());
    }

    private static boolean blank(String sessionId) {
        return sessionId == null || sessionId.isBlank();
    }
}
