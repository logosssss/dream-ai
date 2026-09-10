package com.zhu.ai.gateway;

import com.zhu.ai.kernel.agent.AgentHandler;
import com.zhu.ai.kernel.agent.AgentRegistry;
import com.zhu.ai.kernel.conversation.ConversationPort;
import com.zhu.ai.kernel.conversation.ConversationTurn;
import com.zhu.ai.kernel.knowledge.RetrievePort;
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
        String agentId = request.agentId() == null || request.agentId().isBlank()
                ? DEFAULT_AGENT_ID
                : request.agentId();
        AgentHandler handler = registry.find(agentId)
                .orElseThrow(() -> new IllegalArgumentException("unknown agent: " + agentId));
        String sessionId = request.sessionId();
        String traceId = observe.begin(sessionId, agentId);
        try {
            List<ConversationTurn> history = conversation.history(sessionId);
            String memoryNotes = memory.recall(sessionId);
            List<String> hits = retrieve.retrieve(request.input(), RETRIEVE_TOP_K);
            String retrievedContext = String.join("\n---\n", hits);
            log.info(
                    "gateway load traceId={} session={} historyTurns={} memoryChars={} retrieveHits={}",
                    traceId,
                    sessionId,
                    history.size(),
                    memoryNotes.length(),
                    hits.size());
            AgentInvokeResult result = handler.handle(new AgentInvokeRequest(
                    agentId, sessionId, request.input(), history, memoryNotes, retrievedContext));
            conversation.appendRound(sessionId, request.input(), result.output());
            memory.rememberRound(sessionId, request.input(), result.output());
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
        } catch (RuntimeException ex) {
            InvokeObservation failed = observe.complete(false, ex.getMessage());
            log.warn(
                    "gateway fail traceId={} session={} durationMs={} error={}",
                    failed.traceId(),
                    sessionId,
                    failed.durationMs(),
                    ex.getMessage());
            throw ex;
        }
    }
}
