package com.zhu.ai.config;

import com.zhu.ai.graph.GraphAgentHandler;
import com.zhu.ai.graph.SupervisorGraph;
import com.zhu.ai.kernel.graph.GraphPort;
import com.zhu.ai.kernel.llm.ChatPort;
import com.zhu.ai.kernel.observe.ObservePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Graph 组合根：{@link GraphPort}（Supervisor）+ {@code agentId=graph} Handler。
 */
@Configuration
public class GraphConfig {

    @Bean
    GraphPort graphPort(ChatPort chatPort) {
        return new SupervisorGraph(chatPort);
    }

    @Bean
    GraphAgentHandler graphAgentHandler(GraphPort graphPort, ObservePort observe) {
        return new GraphAgentHandler(graphPort, observe);
    }
}
