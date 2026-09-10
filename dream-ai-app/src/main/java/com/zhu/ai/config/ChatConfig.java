package com.zhu.ai.config;

import com.zhu.ai.kernel.llm.ChatPort;
import com.zhu.ai.kernel.observe.ObservePort;
import com.zhu.ai.kernel.tool.ToolPort;
import com.zhu.ai.llm.DashScopeChatAdapter;
import com.zhu.ai.llm.ToolCallbackPort;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 组合根：仅在 {@code spring.ai.model.chat=dashscope} 时装配。
 * <p>
 * 把 Spring AI 的 {@code ChatModel} / {@code ToolCallback} 收成 kernel 的 {@link ChatPort}、
 * {@link ToolPort}；单测可自行提供这两个 Bean，并设 {@code chat: none}。
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "dashscope")
public class ChatConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatConfig.class);

    /** 广告给模型的工具名，须与 {@link #clockToolCallback()} 里 builder 的 name 一致。 */
    static final String CLOCK_TOOL = "current_date_time";

    private static final DateTimeFormatter CLOCK_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    /**
     * 当前进程内业务工具：上海时区的日期时间。
     * MCP 贡献的工具（最多 1 个）由 {@code McpConfig} 另注册 {@code ToolCallback}，同样进 {@link #toolPort}。
     * 只负责 schema + 执行；何时调用由模型决定，循环和限步在 {@code ToolCallingLoop}。
     */
    @Bean
    ToolCallback clockToolCallback() {
        return FunctionToolCallback.builder(CLOCK_TOOL, () ->
                        ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(CLOCK_FMT))
                .description("返回当前日期和时间（上海时区）。用户问现在几点、今天几号或星期几时必须调用，不要猜测。")
                .build();
    }

    /**
     * 把容器里所有 {@link ToolCallback} 收成 {@link ToolPort}。
     * 再加工具时声明新的 {@code ToolCallback} Bean 即可，不必改适配器。
     */
    @Bean
    @ConditionalOnMissingBean(ToolPort.class)
    ToolPort toolPort(List<ToolCallback> callbacks) {
        log.info(
                "ToolPort registered tools={}",
                callbacks.stream().map(cb -> cb.getToolDefinition().name()).toList());
        return new ToolCallbackPort(callbacks);
    }

    /**
     * 对外只暴露 {@link ChatPort}：内部用 DashScope {@code ChatModel} 做单步调用，
     * 再交给工具循环限步。Agent 不要直接注入 {@code ChatModel}。
     */
    @Bean
    @ConditionalOnMissingBean(ChatPort.class)
    ChatPort chatPort(
            ChatModel chatModel,
            List<ToolCallback> callbacks,
            ToolPort tools,
            ObservePort observe,
            @Value("${spring.ai.dashscope.chat.options.multi-model:#{null}}") Boolean multiModel) {
        return new DashScopeChatAdapter(chatModel, callbacks, tools, observe, multiModel);
    }
}
