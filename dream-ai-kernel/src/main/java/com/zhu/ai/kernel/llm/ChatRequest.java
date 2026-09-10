package com.zhu.ai.kernel.llm;

import com.zhu.ai.kernel.conversation.ConversationTurn;
import java.util.List;

/**
 * 一次补全请求。{@code model} 为空则用装配时的默认模型；{@code system} 可选。
 * {@code history} 是本轮之前的会话，不含当前 {@code prompt}。
 */
public record ChatRequest(String model, String prompt, String system, List<ConversationTurn> history) {

    public ChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public ChatRequest(String model, String prompt, String system) {
        this(model, prompt, system, List.of());
    }

    public ChatRequest(String model, String prompt) {
        this(model, prompt, null, List.of());
    }
}
