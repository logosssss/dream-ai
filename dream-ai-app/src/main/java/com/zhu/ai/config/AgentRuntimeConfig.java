package com.zhu.ai.config;

import com.zhu.ai.gateway.DefaultAgentGateway;
import com.zhu.ai.gateway.InMemoryAgentRegistry;
import com.zhu.ai.kernel.agent.AgentHandler;
import com.zhu.ai.kernel.agent.AgentRegistry;
import com.zhu.ai.kernel.conversation.ConversationPort;
import com.zhu.ai.kernel.knowledge.RetrievePort;
import com.zhu.ai.kernel.memory.MemoryPort;
import com.zhu.ai.kernel.observe.ObservePort;
import com.zhu.ai.kernel.runtime.AgentGateway;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 运行时组合根：名册 + {@link AgentGateway}。各 Port 见 {@link PortsConfig}。
 */
@Configuration
public class AgentRuntimeConfig {

    @Bean
    AgentRegistry agentRegistry(List<AgentHandler> handlers) {
        return new InMemoryAgentRegistry(handlers);
    }

    @Bean
    AgentGateway agentGateway(
            AgentRegistry registry,
            ConversationPort conversation,
            MemoryPort memory,
            RetrievePort retrieve,
            ObservePort observe) {
        return new DefaultAgentGateway(registry, conversation, memory, retrieve, observe);
    }
}
