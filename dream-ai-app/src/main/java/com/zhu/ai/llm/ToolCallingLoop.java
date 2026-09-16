package com.zhu.ai.llm;

import com.zhu.ai.kernel.llm.TokenSink;
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
 * 显式工具调用循环（ReAct 风格的「模型一步 → 工具 → 再模型」）。
 * <p>
 * <b>为什么自己写循环</b>：不依赖 Spring AI / DashScope 的「框架内自动跑工具」，也不走 Alibaba Graph。
 * 步数、拦截语义、观测与 SSE 事件都在本类可见，便于面试讲清停机条件与 HITL。
 * 模型侧须关闭内部 tool execution（见 {@link DashScopeChatAdapter}），由本循环调用 {@link ToolPort}。
 * <p>
 * <b>一轮在做什么</b>：
 * <ol>
 *   <li>{@link ChatStepClient#step}：把当前消息列表交给模型，得到 {@link AssistantMessage}</li>
 *   <li>若无 tool call：把助手文本当作最终答案返回，循环结束</li>
 *   <li>若有 tool call：经 {@link #applyToolCalls} 执行（或被策略拦截），把
 *       assistant + {@link ToolResponseMessage} 追加进 messages，进入下一步</li>
 *   <li>达到 {@link #MAX_STEPS}：返回固定提示文案，避免死循环</li>
 * </ol>
 * <p>
 * <b>与策略 / 观测 / SSE</b>：
 * <ul>
 *   <li>执行口是 {@link ToolPort}（通常已包 {@link GuardedToolPort}：白名单、HITL）</li>
 *   <li>策略拒执：返回串带 {@link GuardedToolPort#DENIED_PREFIX} → 记 {@code blockedTools}，
 *       <em>不</em>计入 {@code toolCalls} / {@code executedTools}</li>
 *   <li>真正执行：记 {@code executedTools}，并可选经 {@link TokenSink} 发 {@code tool_executed}</li>
 *   <li>每次模型步进都会 {@link ObservePort#markModelCall}</li>
 * </ul>
 * <p>
 * <b>谁调用</b>：{@link DashScopeChatAdapter#complete}（同步，一般无 sink）；
 * 流式路径在工具阶段也会 new 本类并传入 SSE 的 {@link TokenSink}。
 * Agent / Graph 不直接依赖本类。
 */
public final class ToolCallingLoop {

    private static final Logger log = LoggerFactory.getLogger(ToolCallingLoop.class);

    /**
     * 最大模型步数（含「最后一次仍要调工具」的那步）。
     * 第 {@code MAX_STEPS} 步若仍带 tool call，不再执行工具，直接返回 {@link #MAX_STEPS_MESSAGE}。
     */
    public static final int MAX_STEPS = 5;

    /** 触顶时返回给用户 / 上层的固定文案（不当作模型幻觉内容）。 */
    public static final String MAX_STEPS_MESSAGE = "已达到工具调用上限（" + MAX_STEPS + " 步），请缩小问题再试。";

    /**
     * 单步模型调用：入参为截至目前的完整对话（含历史 tool 结果），出参为助手消息。
     * 由适配器用 {@code ChatModel#call} 或 stream 收齐后的最终 {@link AssistantMessage} 实现。
     */
    @FunctionalInterface
    public interface ChatStepClient {
        AssistantMessage step(List<Message> messages);
    }

    private final ChatStepClient llm;
    private final ToolPort tools;
    private final ObservePort observe;
    private final TokenSink sink;

    /** 最小构造：无观测、无 SSE。单测与简单场景。 */
    public ToolCallingLoop(ChatStepClient llm, ToolPort tools) {
        this(llm, tools, null, null);
    }

    /** 同步 invoke：上报观测，不推 SSE 工具事件。 */
    public ToolCallingLoop(ChatStepClient llm, ToolPort tools, ObservePort observe) {
        this(llm, tools, observe, null);
    }

    /**
     * 完整构造。
     *
     * @param llm     每步模型调用；不可为 null
     * @param tools   工具执行契约（宜已做策略守卫）
     * @param observe 可为 null（不计 model/tool 观测）
     * @param sink    可为 null（不发 tool_* SSE）；流式工具阶段传入
     */
    public ToolCallingLoop(ChatStepClient llm, ToolPort tools, ObservePort observe, TokenSink sink) {
        this.llm = llm;
        this.tools = tools;
        this.observe = observe;
        this.sink = sink;
    }

    /**
     * 从种子消息跑到「无工具 / 触顶」。
     * <p>
     * {@code seed} 通常含 system + history + 当前 user；本方法会在副本上追加 assistant / tool 消息，
     * 不修改调用方传入的列表。返回值是最终助手纯文本（或触顶提示），不是完整 message 轨迹。
     *
     * @param seed 初始消息；空列表也可，但模型侧通常需要至少一条 user
     * @return 最终回答文本；助手消息为 null 时返回空串
     */
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
            // 最后一步仍要调工具：不再执行，避免「再跑一轮又触顶」的半截状态
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
     * 执行本轮全部 tool call，组装一条 {@link ToolResponseMessage} 喂回模型。
     * <p>
     * 顺序：对每个 call 先 {@link TokenSink#onToolStart}，再 {@link ToolPort#execute}；
     * 若结果为策略拒执 → 观测 blocked + SSE {@code tool_blocked}；
     * 否则 → 观测 executed + SSE {@code tool_executed}。
     * 拒执结果仍写入 ToolResponse，让模型看到「被拒绝」而不是静默丢弃。
     * <p>
     * 包内可见：流式适配器在收齐 tool call 后复用本方法，与同步路径语义一致。
     */
    ToolResponseMessage applyToolCalls(List<AssistantMessage.ToolCall> calls) {
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>(calls.size());
        for (AssistantMessage.ToolCall call : calls) {
            emitToolStart(call.name());
            String output = tools.execute(call.name(), call.arguments());
            if (GuardedToolPort.isPolicyBlock(output)) {
                log.info(
                        "tool blocked name={} id={} args={} result={}",
                        call.name(),
                        call.id(),
                        truncate(call.arguments()),
                        truncate(output));
                markBlocked(call.name());
                emitToolBlocked(call.name());
            } else {
                log.info(
                        "tool executed name={} id={} args={} result={}",
                        call.name(),
                        call.id(),
                        truncate(call.arguments()),
                        truncate(output));
                markExecuted(call.name());
                emitToolExecuted(call.name());
            }
            responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), output));
        }
        return ToolResponseMessage.builder().responses(responses).build();
    }

    /** 模型每步进一次 +1；无 {@link #observe} 则跳过。 */
    private void markModel() {
        if (observe != null) {
            observe.markModelCall();
        }
    }

    /** 真正执行成功的工具名写入观测。 */
    private void markExecuted(String toolName) {
        if (observe != null) {
            observe.markToolExecuted(toolName);
        }
    }

    /** 被策略拦截的工具名写入观测（不计执行次数）。 */
    private void markBlocked(String toolName) {
        if (observe != null) {
            observe.markToolBlocked(toolName);
        }
    }

    /**
     * SSE：工具即将执行（含随后可能被策略拦截的情况）。
     * 无 {@link #sink} 或空工具名则跳过，同步 invoke 不受影响。
     */
    private void emitToolStart(String toolName) {
        if (sink != null && toolName != null && !toolName.isBlank()) {
            sink.onToolStart(toolName);
        }
    }

    /**
     * SSE：工具被白名单 / HITL 拒执，与观测 {@code blockedTools} 对齐。
     */
    private void emitToolBlocked(String toolName) {
        if (sink != null && toolName != null && !toolName.isBlank()) {
            sink.onToolBlocked(toolName);
        }
    }

    /**
     * SSE：工具真正执行成功，与观测 {@code executedTools} 对齐。
     */
    private void emitToolExecuted(String toolName) {
        if (sink != null && toolName != null && !toolName.isBlank()) {
            sink.onToolExecuted(toolName);
        }
    }

    /** 日志用：本轮调用的工具名列表。 */
    private static String toolNames(List<AssistantMessage.ToolCall> calls) {
        return calls.stream().map(AssistantMessage.ToolCall::name).collect(Collectors.joining(","));
    }

    /** 日志截断，避免把超长 arguments / 工具输出打满盘。 */
    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
