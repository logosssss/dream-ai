package com.zhu.ai.config;

import com.zhu.ai.graph.SupervisorGraph;
import com.zhu.ai.kernel.graph.GraphPort;
import com.zhu.ai.kernel.llm.ChatPort;
import com.zhu.ai.kernel.llm.ModelRouter;
import com.zhu.ai.kernel.observe.ObservePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Graph 组合根：只装配 {@link GraphPort}（Supervisor 实现）。
 * {@code agentId=graph} 入口在 agents 的 {@code GraphAgent}（组件扫描），不在此再注册 Bean。
 */
@Configuration
public class GraphConfig {

    @Bean
    GraphPort graphPort(ChatPort chatPort, ModelRouter modelRouter, ObservePort observe) {
        return new SupervisorGraph(chatPort, modelRouter, observe);
    }
}
