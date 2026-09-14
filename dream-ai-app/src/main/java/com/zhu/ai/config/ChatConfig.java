package com.zhu.ai.config;

import com.zhu.ai.kernel.llm.ChatPort;
import com.zhu.ai.kernel.observe.ObservePort;
import com.zhu.ai.kernel.tool.ToolPort;
import com.zhu.ai.llm.DashScopeChatAdapter;
import com.zhu.ai.llm.DatetimeOffsetTool;
import com.zhu.ai.llm.LocalMethodTools;
import com.zhu.ai.llm.ToolCallbackPort;
import com.zhu.ai.llm.ToolCallbackSupport;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 组合根：仅在 {@code spring.ai.model.chat=dashscope} 时装配。
 * <p>
 * 工具贡献三条路径最终都收成 {@link ToolCallback}，再进 {@link ToolPort} / 模型 options：
 * <ul>
 *   <li>{@link FunctionToolCallback} — 本类 clock / datetime_offset</li>
 *   <li>{@code MethodToolCallback} — {@link LocalMethodTools} + {@link MethodToolCallbackProvider}</li>
 *   <li>{@code SyncMcpToolCallback} / {@code AsyncMcpToolCallback} — {@code McpConfig} 按 mode 二选一，
 *       {@code list_tools} 全部经 {@link ToolCallbackProvider} 合并</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "dashscope")
public class ChatConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatConfig.class);

    /** 广告给模型的工具名，须与 {@link #clockToolCallback()} 里 builder 的 name 一致。 */
    static final String CLOCK_TOOL = "current_date_time";

    private static final DateTimeFormatter CLOCK_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    @Bean
    LocalMethodTools localMethodTools() {
        return new LocalMethodTools();
    }

    /**
     * {@code MethodToolCallback} 贡献口：扫 {@link LocalMethodTools} 上的 {@code @Tool}。
     * 与直接 {@link ToolCallback} Bean 一并在 {@link #toolPort} 合并。
     */
    @Bean
    ToolCallbackProvider localMethodToolCallbackProvider(LocalMethodTools localMethodTools) {
        return MethodToolCallbackProvider.builder().toolObjects(localMethodTools).build();
    }

    /**
     * 无参进程内工具：{@link FunctionToolCallback}。
     * MCP 由 {@code McpConfig} 以 {@link ToolCallbackProvider} 注册全部远端工具；何时调用由模型决定，循环和限步在 {@code ToolCallingLoop}。
     */
    @Bean
    ToolCallback clockToolCallback() {
        return FunctionToolCallback.builder(CLOCK_TOOL, () ->
                        ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(CLOCK_FMT))
                .description("返回当前日期和时间（上海时区）。用户问现在几点、今天几号或星期几时必须调用，不要猜测。")
                .build();
    }

    /**
     * 带参进程内工具：{@link FunctionToolCallback} + inputType。
     */
    @Bean
    ToolCallback datetimeOffsetToolCallback() {
        return FunctionToolCallback.builder(
                        DatetimeOffsetTool.TOOL_NAME, DatetimeOffsetTool::execute)
                .description(
                        "按指定时区对基准时间做加减（天/小时/分钟），返回换算后的日期时间与中文星期。"
                                + "用户问「三天后是几号」「往后推 N 小时」「某时区某时刻加减」时必须调用，不要心算。"
                                + "unit 仅支持 DAYS、HOURS、MINUTES；baseDateTime 可空（表示该时区当前时刻）；"
                                + "zoneId 可空（默认 Asia/Shanghai）。")
                .inputType(DatetimeOffsetTool.Request.class)
                .build();
    }

    /**
     * 合并直接 {@link ToolCallback} Bean 与所有 {@link ToolCallbackProvider}（含 Method 路径）。
     */
    @Bean
    @ConditionalOnMissingBean(ToolPort.class)
    ToolPort toolPort(List<ToolCallback> callbacks, List<ToolCallbackProvider> providers) {
        List<ToolCallback> merged = ToolCallbackSupport.merge(callbacks, providers);
        log.info("ToolPort registered tools={}", ToolCallbackSupport.summarize(merged));
        return new ToolCallbackPort(merged);
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
            List<ToolCallbackProvider> providers,
            ToolPort tools,
            ObservePort observe,
            @Value("${spring.ai.dashscope.chat.options.multi-model:#{null}}") Boolean multiModel) {
        List<ToolCallback> merged = ToolCallbackSupport.merge(callbacks, providers);
        return new DashScopeChatAdapter(chatModel, merged, tools, observe, multiModel);
    }
}
