package com.zhu.ai.agent.chat;

import com.zhu.ai.kernel.agent.AgentHandler;
import com.zhu.ai.kernel.llm.ChatPort;
import com.zhu.ai.kernel.llm.ChatRequest;
import com.zhu.ai.kernel.runtime.AgentInvokeRequest;
import com.zhu.ai.kernel.runtime.AgentInvokeResult;
import org.springframework.stereotype.Component;

/**
 * 对话助手（当前唯一业务 Agent）。
 * <p>
 * 写 system prompt 和入参校验；把 Gateway 填好的 history / 长期记忆 / 检索片段交给 {@link ChatPort}，
 * 不注入会话、记忆或知识存储。
 */
@Component
public class ChatAgent implements AgentHandler {

    public static final String ID = "chat";

    private static final String SYSTEM =
            "你是对话助手，用简洁中文回答用户问题。\n"
                    + "不知道就说不知道，不要编造。\n"
                    + "需要当前真实时间或日期时必须调用 current_date_time，不要猜测。\n"
                    + "需要对某时刻做加减（几天后、往后 N 小时/分钟、跨时区换算基准）时必须调用 datetime_offset，不要心算。\n"
                    + "需要统计一段文本有多长、几个字、是否含中文时必须调用 text_stats，不要心算。\n"
                    + "需要联网查资料、新闻、天气、股价等时效信息时：若有 web_search 工具则必须先调用，不要凭记忆编造。\n"
                    + "参考资料只覆盖仓库知识；时效问题不要用参考资料代替联网搜索。\n";

    private final ChatPort chatPort;

    public ChatAgent(ChatPort chatPort) {
        this.chatPort = chatPort;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "对话助手";
    }

    @Override
    public AgentInvokeResult handle(AgentInvokeRequest request) {
        String input = request.input();
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input required");
        }
        String output = chatPort.complete(
                new ChatRequest(null, input, systemPrompt(request.memoryNotes(), request.retrievedContext()), request.history()));
        return new AgentInvokeResult(ID, output);
    }

    static String systemPrompt(String memoryNotes, String retrievedContext) {
        StringBuilder system = new StringBuilder(SYSTEM);
        if (memoryNotes != null && !memoryNotes.isBlank()) {
            system.append("\n长期记忆：\n").append(memoryNotes);
        }
        if (retrievedContext != null && !retrievedContext.isBlank()) {
            system.append("\n参考资料（知识问题优先依据这些片段，没有则说不知道）：\n")
                    .append(retrievedContext);
        }
        return system.toString();
    }
}