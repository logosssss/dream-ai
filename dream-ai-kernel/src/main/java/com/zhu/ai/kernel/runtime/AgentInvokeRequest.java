package com.zhu.ai.kernel.runtime;

import com.zhu.ai.kernel.conversation.ConversationTurn;
import java.util.List;

/**
 * Gateway 入参。HTTP JSON 不含 history / memoryNotes / retrievedContext，由 Gateway 填入。
 */
public record AgentInvokeRequest(
        String agentId,
        String sessionId,
        String input,
        List<ConversationTurn> history,
        String memoryNotes,
        String retrievedContext) {

    public AgentInvokeRequest {
        history = history == null ? List.of() : List.copyOf(history);
        memoryNotes = memoryNotes == null ? "" : memoryNotes;
        retrievedContext = retrievedContext == null ? "" : retrievedContext;
    }

    public AgentInvokeRequest(String agentId, String sessionId, String input) {
        this(agentId, sessionId, input, List.of(), "", "");
    }

    public AgentInvokeRequest(String agentId, String sessionId, String input, List<ConversationTurn> history) {
        this(agentId, sessionId, input, history, "", "");
    }

    public AgentInvokeRequest(
            String agentId, String sessionId, String input, List<ConversationTurn> history, String memoryNotes) {
        this(agentId, sessionId, input, history, memoryNotes, "");
    }
}
