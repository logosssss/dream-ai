package com.zhu.ai.gateway;

import com.zhu.ai.kernel.agent.AgentHandler;
import com.zhu.ai.kernel.agent.AgentRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 进程内 {@link AgentRegistry}：启动时把 Spring 收集到的 {@link AgentHandler} 做成不可变 Map。
 * 重复 {@code id} 时抛 {@link IllegalStateException}，避免静默覆盖。
 */
public final class InMemoryAgentRegistry implements AgentRegistry {

    private final Map<String, AgentHandler> handlers;

    public InMemoryAgentRegistry(List<AgentHandler> handlers) {
        Map<String, AgentHandler> map = new LinkedHashMap<>();
        for (AgentHandler handler : handlers) {
            AgentHandler previous = map.put(handler.id(), handler);
            if (previous != null) {
                throw new IllegalStateException(
                        "duplicate agent id '" + handler.id() + "': "
                                + previous.getClass().getName() + " and " + handler.getClass().getName());
            }
        }
        this.handlers = Map.copyOf(map);
    }

    @Override
    public Optional<AgentHandler> find(String agentId) {
        return Optional.ofNullable(handlers.get(agentId));
    }
}
