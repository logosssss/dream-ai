package com.zhu.ai.llm;

import com.zhu.ai.kernel.observe.ObservePort;
import com.zhu.ai.kernel.tool.ToolPort;
import com.zhu.ai.tool.GuardedToolPort;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

/**
 * 显式工具循环：模型一步 → 若有 tool call 则执行再喂回，直到无工具或达到 {@link #MAX_STEPS}。
 * 停在本类，不依赖 Alibaba Graph；内部执行关掉，步数自己数。
 */
public final class ToolCallingLoop {

    private static final Logger log = LoggerFactory.getLogger(ToolCallingLoop.class);

    public static final int MAX_STEPS = 5;

    public static final String MAX_STEPS_MESSAGE = "已达到工具调用上限（" + MAX_STEPS + " 步），请缩小问题再试。";

    @FunctionalInterface
    public interface ChatStepClient {
        AssistantMessage step(List<Message> messages);
    }

    private final ChatStepClient llm;
    private final ToolPort tools;
    private final ObservePort observe;

    public ToolCallingLoop(ChatStepClient llm, ToolPort tools) {
        this(llm, tools, null);
    }

    public ToolCallingLoop(ChatStepClient llm, ToolPort tools, ObservePort observe) {
        this.llm = llm;
        this.tools = tools;
        this.observe = observe;
    }

    public String run(List<Message> seed) {
        List<Message> messages = new ArrayList<>(seed);
        for (int i = 0; i < MAX_STEPS; i++) {
            int step = i + 1;
            AssistantMessage assistant = llm.step(List.copyOf(messages));
            markModel();
            if (assistant == null) {
                log.info("llm step {}: empty assistant message", step);
                return "";
            }
            if (!assistant.hasToolCalls()) {
                if (i == 0) {
                    log.info("llm step {}: no tool call", step);
                } else {
                    log.info("llm step {}: no more tool calls (after {} tool round(s))", step, i);
                }
                String text = assistant.getText();
                return text != null ? text : "";
            }
            List<AssistantMessage.ToolCall> calls = assistant.getToolCalls();
            log.info("llm step {}: tool call(s) {}", step, toolNames(calls));
            if (i == MAX_STEPS - 1) {
                log.warn("tool loop hit max steps {}", MAX_STEPS);
                return MAX_STEPS_MESSAGE;
            }
            messages.add(assistant);
            messages.add(applyToolCalls(calls));
        }
        log.warn("tool loop hit max steps {}", MAX_STEPS);
        return MAX_STEPS_MESSAGE;
    }

    /**
     * 执行本轮 tool call：策略拦截只记 blocked，不计入 {@code toolCalls}；
     * 真正执行才打 executed 并上报观测。
     */
    ToolResponseMessage applyToolCalls(List<AssistantMessage.ToolCall> calls) {
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>(calls.size());
        for (AssistantMessage.ToolCall call : calls) {
            String output = tools.execute(call.name(), call.arguments());
            if (GuardedToolPort.isPolicyBlock(output)) {
                log.info(
                        "tool blocked name={} id={} args={} result={}",
                        call.name(),
                        call.id(),
                        truncate(call.arguments()),
                        truncate(output));
                markBlocked(call.name());
            } else {
                log.info(
                        "tool executed name={} id={} args={} result={}",
                        call.name(),
                        call.id(),
                        truncate(call.arguments()),
                        truncate(output));
                markExecuted(call.name());
            }
            responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), output));
        }
        return ToolResponseMessage.builder().responses(responses).build();
    }

    private void markModel() {
        if (observe != null) {
            observe.markModelCall();
        }
    }

    private void markExecuted(String toolName) {
        if (observe != null) {
            observe.markToolExecuted(toolName);
        }
    }

    private void markBlocked(String toolName) {
        if (observe != null) {
            observe.markToolBlocked(toolName);
        }
    }

    private static String toolNames(List<AssistantMessage.ToolCall> calls) {
        return calls.stream().map(AssistantMessage.ToolCall::name).collect(Collectors.joining(","));
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
