package com.zhu.ai.web;

import com.zhu.ai.kernel.knowledge.RetrieveHitSummary;
import com.zhu.ai.kernel.llm.StreamCancelledException;
import com.zhu.ai.kernel.llm.TokenSink;
import com.zhu.ai.kernel.runtime.AgentGateway;
import com.zhu.ai.kernel.runtime.AgentInvokeResult;
import com.zhu.ai.tool.ToolApprovalContext;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 唯一 HTTP 入口。JSON 用 {@link AgentInvokeHttpRequest} / {@link AgentInvokeHttpResponse}，
 * 只调 {@link AgentGateway}，不注入具体 Agent 或 ChatPort。
 * <p>
 * HITL：可选头 {@code X-Dream-Tool-Approvals: toolA,toolB}，配合 {@code dream.tools.hitl-mode=enforce}。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentInvokeController {

    public static final String TOOL_APPROVALS_HEADER = "X-Dream-Tool-Approvals";

    private final AgentGateway agentGateway;
    private final TaskExecutor streamExecutor;
    private final long streamTimeoutMs;

    public AgentInvokeController(
            AgentGateway agentGateway,
            @Qualifier("agentStreamExecutor") TaskExecutor streamExecutor,
            @Value("${dream.stream.timeout-ms:120000}") long streamTimeoutMs) {
        this.agentGateway = agentGateway;
        this.streamExecutor = streamExecutor;
        this.streamTimeoutMs = streamTimeoutMs > 0 ? streamTimeoutMs : 120_000L;
    }

    @PostMapping("/invoke")
    public AgentInvokeHttpResponse invoke(
            @RequestBody AgentInvokeHttpRequest request,
            @RequestHeader(value = TOOL_APPROVALS_HEADER, required = false) String approvals) {
        Set<String> approved = ToolApprovalContext.parseHeader(approvals);
        ToolApprovalContext.open(approved);
        try {
            return AgentInvokeHttpResponse.from(agentGateway.invoke(request.toKernel()));
        } finally {
            ToolApprovalContext.close();
        }
    }

    /**
     * SSE 事件：
     * <ul>
     *   <li>{@code retrieve} — 检索摘要（id/score/source），与 prompt {@code [1]} 对照</li>
 *   <li>{@code route} — Graph 选中叶，data 为 {@code {"route":"review"}}</li>
     *   <li>{@code model} — 本轮选用模型，data 为 {@code {"model":"qwen-turbo"}}</li>
     *   <li>{@code tool_start} / {@code tool_blocked} / {@code tool_executed} — 工具生命周期</li>
     *   <li>{@code delta} — 文本增量</li>
     *   <li>{@code done} — 完整 {@link AgentInvokeHttpResponse}</li>
     *   <li>{@code error} — 失败</li>
     * </ul>
     */
    @PostMapping(value = "/invoke/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter invokeStream(
            @RequestBody AgentInvokeHttpRequest request,
            @RequestHeader(value = TOOL_APPROVALS_HEADER, required = false) String approvals) {
        SseEmitter emitter = new SseEmitter(streamTimeoutMs);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Set<String> approved = ToolApprovalContext.parseHeader(approvals);
        emitter.onTimeout(() -> cancelled.set(true));
        emitter.onError(ex -> cancelled.set(true));
        streamExecutor.execute(() -> pump(emitter, request, cancelled, approved));
        return emitter;
    }

    private void pump(
            SseEmitter emitter,
            AgentInvokeHttpRequest request,
            AtomicBoolean cancelled,
            Set<String> approved) {
        ToolApprovalContext.open(approved);
        try {
            AgentInvokeResult result = agentGateway.invokeStream(request.toKernel(), new TokenSink() {
                @Override
                public void onDelta(String delta) {
                    sendNamed(emitter, cancelled, "delta", delta);
                }

                @Override
                public void onRetrieve(List<RetrieveHitSummary> hits) {
                    sendNamed(emitter, cancelled, "retrieve", hits);
                }

                @Override
                public void onRoute(String route) {
                    sendNamed(emitter, cancelled, "route", toolPayload("route", route));
                }

                @Override
                public void onModel(String model) {
                    sendNamed(emitter, cancelled, "model", toolPayload("model", model));
                }

                @Override
                public void onToolStart(String toolName) {
                    sendNamed(emitter, cancelled, "tool_start", toolPayload("tool", toolName));
                }

                @Override
                public void onToolBlocked(String toolName) {
                    sendNamed(emitter, cancelled, "tool_blocked", toolPayload("tool", toolName));
                }

                @Override
                public void onToolExecuted(String toolName) {
                    sendNamed(emitter, cancelled, "tool_executed", toolPayload("tool", toolName));
                }

                @Override
                public boolean cancelled() {
                    return cancelled.get();
                }
            });
            emitter.send(SseEmitter.event().name("done").data(AgentInvokeHttpResponse.from(result)));
            emitter.complete();
        } catch (StreamCancelledException ex) {
            emitter.complete();
        } catch (Exception ex) {
            String message = ex.getMessage();
            if (message == null || message.isBlank()) {
                message = ex.getClass().getSimpleName();
            }
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("error", message)));
            } catch (Exception ignored) {
                // already closed
            }
            emitter.completeWithError(ex);
        } finally {
            ToolApprovalContext.close();
        }
    }

    private static Map<String, String> toolPayload(String key, String value) {
        Map<String, String> body = new LinkedHashMap<>(1);
        body.put(key, value == null ? "" : value);
        return body;
    }

    private static void sendNamed(
            SseEmitter emitter, AtomicBoolean cancelled, String event, Object data) {
        if (cancelled.get() || data == null) {
            return;
        }
        if (data instanceof String text && text.isEmpty()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException ex) {
            cancelled.set(true);
            throw new StreamCancelledException("sse send failed", ex);
        }
    }
}
