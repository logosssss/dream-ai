package com.zhu.ai.llm;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.zhu.ai.kernel.conversation.ConversationTurn;
import com.zhu.ai.kernel.llm.ChatPort;
import com.zhu.ai.kernel.llm.ChatRequest;
import com.zhu.ai.kernel.observe.ObservePort;
import com.zhu.ai.kernel.tool.ToolPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;

/**
 * ChatModel → {@link ChatPort}：拼 prompt，再跑 {@link ToolCallingLoop}。
 * <p>
 * 必须用 {@link DashScopeChatOptions}（不能只用 {@code ToolCallingChatOptions}），
 * 否则 {@code multiModel=true} 会在合并时被冲掉，qwen3.8-* 等会打到纯文本端点并报 url error。
 */
public final class DashScopeChatAdapter implements ChatPort {

    private final ChatModel chatModel;
    private final List<ToolCallback> toolCallbacks;
    private final ToolPort tools;
    private final ObservePort observe;
    private final Boolean multiModel;

    public DashScopeChatAdapter(
            ChatModel chatModel,
            List<ToolCallback> toolCallbacks,
            ToolPort tools,
            ObservePort observe,
            Boolean multiModel) {
        this.chatModel = chatModel;
        this.toolCallbacks = List.copyOf(toolCallbacks);
        this.tools = tools;
        this.observe = observe;
        this.multiModel = multiModel;
    }

    @Override
    public String complete(ChatRequest request) {
        ToolCallingLoop loop =
                new ToolCallingLoop(messages -> oneStep(messages, request.model()), tools, observe);
        return loop.run(seedMessages(request));
    }

    private AssistantMessage oneStep(List<Message> messages, String model) {
        DashScopeChatOptions.DashScopeChatOptionsBuilder options = DashScopeChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(false);
        if (StringUtils.hasText(model)) {
            options.model(model);
        }
        options.multiModel(resolveMultiModel(model));
        ChatResponse response = chatModel.call(new Prompt(messages, options.build()));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return new AssistantMessage("");
        }
        return response.getResult().getOutput();
    }

    /**
     * 配置显式优先；未配时按模型名推断（qwen3.8 / qwen3.5 / vl / kimi-k2 等走多模态端点）。
     */
    boolean resolveMultiModel(String runtimeModel) {
        if (multiModel != null) {
            return multiModel;
        }
        return looksMultimodal(runtimeModel);
    }

    static boolean looksMultimodal(String model) {
        if (!StringUtils.hasText(model)) {
            return false;
        }
        String m = model.toLowerCase(Locale.ROOT);
        return m.contains("qwen3.8")
                || m.contains("qwen3.5")
                || m.contains("qwen-vl")
                || m.contains("qwen3-vl")
                || m.contains("kimi-k2");
    }

    static List<Message> seedMessages(ChatRequest request) {
        List<Message> messages = new ArrayList<>();
        if (StringUtils.hasText(request.system())) {
            messages.add(new SystemMessage(request.system()));
        }
        for (ConversationTurn turn : request.history()) {
            String content = turn.content() == null ? "" : turn.content();
            messages.add(switch (turn.role()) {
                case USER -> new UserMessage(content);
                case ASSISTANT -> new AssistantMessage(content);
            });
        }
        messages.add(new UserMessage(request.prompt() == null ? "" : request.prompt()));
        return messages;
    }
}
