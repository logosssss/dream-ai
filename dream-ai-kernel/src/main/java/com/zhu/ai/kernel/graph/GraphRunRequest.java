package com.zhu.ai.kernel.graph;

import com.zhu.ai.kernel.conversation.ConversationTurn;
import java.util.List;

/**
 * {@link GraphPort#run} 入参。history / memory / retrieve 由 Gateway 装好后传入，
 * 与单 Agent 路径一致；图实现不应再查库。
 */
public record GraphRunRequest(
        String input, List<ConversationTurn> history, String memoryNotes, String retrievedContext) {

    public GraphRunRequest {
        history = history == null ? List.of() : List.copyOf(history);
        memoryNotes = memoryNotes == null ? "" : memoryNotes;
        retrievedContext = retrievedContext == null ? "" : retrievedContext;
        input = input == null ? "" : input;
    }
}
