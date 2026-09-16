package com.zhu.ai.kernel.graph;

import com.zhu.ai.kernel.conversation.ConversationTurn;
import com.zhu.ai.kernel.llm.TokenSink;
import java.util.List;

/**
 * {@link GraphPort#run} 入参。history / memory / retrieve 由 Gateway 装好后传入，
 * 与单 Agent 路径一致；图实现不应再查库。
 * <p>
 * {@code sink} 非空时叶节点应走 {@code ChatPort#stream}，并尽早上报 {@code route}/{@code model}。
 */
public record GraphRunRequest(
        String input,
        List<ConversationTurn> history,
        String memoryNotes,
        String retrievedContext,
        TokenSink sink) {

    public GraphRunRequest {
        history = history == null ? List.of() : List.copyOf(history);
        memoryNotes = memoryNotes == null ? "" : memoryNotes;
        retrievedContext = retrievedContext == null ? "" : retrievedContext;
        input = input == null ? "" : input;
    }

    /** 同步调用：无 SSE sink。 */
    public GraphRunRequest(
            String input, List<ConversationTurn> history, String memoryNotes, String retrievedContext) {
        this(input, history, memoryNotes, retrievedContext, null);
    }
}
