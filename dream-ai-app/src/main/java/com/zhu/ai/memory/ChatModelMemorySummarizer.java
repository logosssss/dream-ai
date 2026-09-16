package com.zhu.ai.memory;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 用 {@link ChatModel} 做摘要，不经带工具的 {@code ChatPort}，避免压缩笔记时误调工具。
 */
public final class ChatModelMemorySummarizer implements MemorySummarizer {

    private static final String SYSTEM =
            "将下列长期记忆压缩为简洁中文要点，保留人名、偏好、约定与关键事实；"
                    + "不要解释过程，不要调用工具，不要编造原文没有的信息。";

    private final ChatModel chatModel;
    private final Boolean multiModel;

    public ChatModelMemorySummarizer(ChatModel chatModel, Boolean multiModel) {
        this.chatModel = chatModel;
        this.multiModel = multiModel;
    }

    @Override
    public String summarize(String notes, int targetMaxChars) {
        String user =
                "请把内容压缩到约 "
                        + targetMaxChars
                        + " 字以内。\n\n---\n"
                        + notes
                        + "\n---";
        DashScopeChatOptions.DashScopeChatOptionsBuilder builder = DashScopeChatOptions.builder();
        if (multiModel != null) {
            builder.multiModel(multiModel);
        }
        Prompt prompt = new Prompt(List.of(new SystemMessage(SYSTEM), new UserMessage(user)), builder.build());
        ChatResponse response = chatModel.call(prompt);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }
}
