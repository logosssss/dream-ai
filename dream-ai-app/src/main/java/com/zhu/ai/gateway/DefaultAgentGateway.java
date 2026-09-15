package com.zhu.ai.gateway;

import com.zhu.ai.kernel.agent.AgentHandler;
import com.zhu.ai.kernel.agent.AgentRegistry;
import com.zhu.ai.kernel.conversation.ConversationPort;
import com.zhu.ai.kernel.conversation.ConversationTurn;
import com.zhu.ai.kernel.knowledge.RetrievePort;
import com.zhu.ai.kernel.llm.StreamCancelledException;
import com.zhu.ai.kernel.llm.TokenSink;
import com.zhu.ai.kernel.memory.MemoryPort;
import com.zhu.ai.kernel.observe.InvokeObservation;
import com.zhu.ai.kernel.observe.ObservePort;
import com.zhu.ai.kernel.runtime.AgentGateway;
import com.zhu.ai.kernel.runtime.AgentInvokeRequest;
import com.zhu.ai.kernel.runtime.AgentInvokeResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link AgentGateway} 默认实现：分发 + 短会话 + 长期记忆 + 检索 + 观测。
 * Agent 不接触这些端口。
 */
public final class DefaultAgentGateway implements AgentGateway {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentGateway.class);

    public static final String DEFAULT_AGENT_ID = "chat";

    static final int RETRIEVE_TOP_K = 3;

    private final AgentRegistry registry;
    private final ConversationPort conversation;
    private final MemoryPort memory;
    private final RetrievePort retrieve;
    private final ObservePort observe;

    public DefaultAgentGateway(
            AgentRegistry registry,
            ConversationPort conversation,
            MemoryPort memory,
            RetrievePort retrieve,
            ObservePort observe) {
        this.registry = registry;
        this.conversation = conversation;
        this.memory = memory;
        this.retrieve = retrieve;
        this.observe = observe;
    }

    @Override
    public AgentInvokeResult invoke(AgentInvokeRequest request) {
        return run(request, AgentHandler::handle);
    }

    @Override
    public AgentInvokeResult invokeStream(AgentInvokeRequest request, TokenSink sink) {
        return run(request, (handler, loaded) -> handler.handleStream(loaded, sink));
    }

    @FunctionalInterface
    private interface HandleFn {
        AgentInvokeResult apply(AgentHandler handler, AgentInvokeRequest loaded);
    }

    private AgentInvokeResult run(AgentInvokeRequest request, HandleFn handle) {
        String agentId = resolveAgentId(request.agentId());
        AgentHandler handler = requireHandler(agentId);
        String sessionId = request.sessionId();
        String traceId = observe.begin(sessionId, agentId);
        try {
            AgentInvokeRequest loaded = loadContext(traceId, agentId, sessionId, request.input());
            AgentInvokeResult result = handle.apply(handler, loaded);
            return persistSuccess(sessionId, request.input(), result);
        } catch (StreamCancelledException ex) {
            observe.complete(false, "cancelled");
            throw ex;
        } catch (RuntimeException ex) {
            fail(sessionId, ex);
            throw ex;
        }
    }

    private static String resolveAgentId(String agentId) {
        return agentId == null || agentId.isBlank() ? DEFAULT_AGENT_ID : agentId;
    }

    private AgentHandler requireHandler(String agentId) {
        return registry.find(agentId)
                .orElseThrow(() -> new IllegalArgumentException("unknown agent: " + agentId));
    }

    /** 装 history / memory / retrieve，供 Agent 消费；不改会话存储。 */
    private AgentInvokeRequest loadContext(String traceId, String agentId, String sessionId, String input) {
        List<ConversationTurn> history = conversation.history(sessionId);
        String memoryNotes = memory.recall(sessionId);
        List<String> hits = retrieve.retrieve(input, RETRIEVE_TOP_K);
        log.info(
                "gateway load traceId={} session={} historyTurns={} memoryChars={} retrieveHits={}",
                traceId,
                sessionId,
                history.size(),
                memoryNotes.length(),
                hits.size());
        return new AgentInvokeRequest(
                agentId, sessionId, input, history, memoryNotes, String.join("\n---\n", hits));
    }

    /** 成功后写短会话 + 长期记忆，并挂上观测摘要。 */
    private AgentInvokeResult persistSuccess(String sessionId, String input, AgentInvokeResult result) {
        conversation.appendRound(sessionId, input, result.output());
        memory.rememberRound(sessionId, input, result.output());
        InvokeObservation observation = observe.complete(true, null);
        log.info(
                "gateway store traceId={} session={} outputChars={} durationMs={} modelCalls={} toolCalls={}",
                observation.traceId(),
                sessionId,
                result.output() == null ? 0 : result.output().length(),
                observation.durationMs(),
                observation.modelCalls(),
                observation.toolCalls());
        return new AgentInvokeResult(result.agentId(), result.output(), observation);
    }

    private void fail(String sessionId, RuntimeException ex) {
        InvokeObservation failed = observe.complete(false, ex.getMessage());
        log.warn(
                "gateway fail traceId={} session={} durationMs={} error={}",
                failed.traceId(),
                sessionId,
                failed.durationMs(),
                ex.getMessage());
    }
}
