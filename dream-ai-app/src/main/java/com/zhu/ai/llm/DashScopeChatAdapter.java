package com.zhu.ai.llm;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.zhu.ai.kernel.conversation.ConversationTurn;
import com.zhu.ai.kernel.llm.ChatPort;
import com.zhu.ai.kernel.llm.ChatRequest;
import com.zhu.ai.kernel.llm.StreamCancelledException;
import com.zhu.ai.kernel.llm.TokenSink;
import com.zhu.ai.kernel.observe.ObservePort;
import com.zhu.ai.kernel.tool.ToolPort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
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
    private final long streamTimeoutMs;

    public DashScopeChatAdapter(
            ChatModel chatModel,
            List<ToolCallback> toolCallbacks,
            ToolPort tools,
            ObservePort observe,
            Boolean multiModel) {
        this(chatModel, toolCallbacks, tools, observe, multiModel, 120_000L);
    }

    public DashScopeChatAdapter(
            ChatModel chatModel,
            List<ToolCallback> toolCallbacks,
            ToolPort tools,
            ObservePort observe,
            Boolean multiModel,
            long streamTimeoutMs) {
        this.chatModel = chatModel;
        this.toolCallbacks = List.copyOf(toolCallbacks);
        this.tools = tools;
        this.observe = observe;
        this.multiModel = multiModel;
        this.streamTimeoutMs = streamTimeoutMs > 0 ? streamTimeoutMs : 120_000L;
    }

    @Override
    public String complete(ChatRequest request) {
        ToolCallingLoop loop =
                new ToolCallingLoop(messages -> oneStep(messages, request.model()), tools, observe);
        return loop.run(seedMessages(request));
    }

    @Override
    public String stream(ChatRequest request, TokenSink sink) {
        List<Message> messages = seedMessages(request);
        for (int i = 0; i < ToolCallingLoop.MAX_STEPS; i++) {
            if (sink != null && sink.cancelled()) {
                throw new StreamCancelledException();
            }
            AssistantMessage assistant = oneStepStream(messages, request.model(), sink);
            if (observe != null) {
                observe.markModelCall();
            }
            if (assistant == null) {
                return "";
            }
            if (!assistant.hasToolCalls()) {
                String text = assistant.getText();
                return text != null ? text : "";
            }
            List<AssistantMessage.ToolCall> calls = assistant.getToolCalls();
            if (i == ToolCallingLoop.MAX_STEPS - 1) {
                return ToolCallingLoop.MAX_STEPS_MESSAGE;
            }
            if (observe != null) {
                observe.markToolCalls(calls.size());
            }
            messages.add(assistant);
            messages.add(toolResponses(calls));
        }
        return ToolCallingLoop.MAX_STEPS_MESSAGE;
    }

    private AssistantMessage oneStep(List<Message> messages, String model) {
        ChatResponse response = chatModel.call(new Prompt(messages, chatOptions(model)));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return new AssistantMessage("");
        }
        return response.getResult().getOutput();
    }

    /**
     * 模型 stream：有 tool call 的分片不推给用户；纯文本分片按累计/增量两种形态拆 delta。
     */
    private AssistantMessage oneStepStream(List<Message> messages, String model, TokenSink sink) {
        StringBuilder acc = new StringBuilder();
        AtomicReference<AssistantMessage> last = new AtomicReference<>(new AssistantMessage(""));
        try {
            chatModel.stream(new Prompt(messages, chatOptions(model)))
                    .doOnNext(response -> {
                        if (sink != null && sink.cancelled()) {
                            throw new StreamCancelledException();
                        }
                        if (response == null
                                || response.getResult() == null
                                || response.getResult().getOutput() == null) {
                            return;
                        }
                        AssistantMessage out = response.getResult().getOutput();
                        last.set(out);
                        if (out.hasToolCalls()) {
                            return;
                        }
                        String delta = nextDelta(acc, out.getText());
                        if (delta.isEmpty()) {
                            return;
                        }
                        acc.append(delta);
                        if (sink != null) {
                            sink.onDelta(delta);
                        }
                    })
                    .blockLast(Duration.ofMillis(streamTimeoutMs));
        } catch (StreamCancelledException ex) {
            throw ex;
        } catch (IllegalStateException ex) {
            throw new org.springframework.ai.retry.TransientAiException(
                    "stream timeout after " + streamTimeoutMs + "ms", ex);
        }
        AssistantMessage held = last.get();
        if ((held.getText() == null || held.getText().isBlank()) && !acc.isEmpty()) {
            return new AssistantMessage(acc.toString());
        }
        return held;
    }

    static String nextDelta(StringBuilder acc, String incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return "";
        }
        String soFar = acc.toString();
        if (incoming.startsWith(soFar)) {
            return incoming.substring(soFar.length());
        }
        return incoming;
    }

    private DashScopeChatOptions chatOptions(String model) {
        DashScopeChatOptions.DashScopeChatOptionsBuilder options = DashScopeChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(false);
        if (StringUtils.hasText(model)) {
            options.model(model);
        }
        options.multiModel(resolveMultiModel(model));
        return options.build();
    }

    private ToolResponseMessage toolResponses(List<AssistantMessage.ToolCall> calls) {
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>(calls.size());
        for (AssistantMessage.ToolCall call : calls) {
            String output = tools.execute(call.name(), call.arguments());
            responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), output));
        }
        return ToolResponseMessage.builder().responses(responses).build();
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
