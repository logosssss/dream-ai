package com.zhu.ai.config;

import com.zhu.ai.graph.GraphAgentHandler;
import com.zhu.ai.kernel.llm.ChatPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Graph Agent 组合根。与 {@code ChatAgent} 一样依赖 {@link ChatPort}；
 * 不用 {@code @ConditionalOnBean}，以便测试 Stub ChatPort 也能挂上 {@code graph}。
 */
@Configuration
public class GraphConfig {

    @Bean
    GraphAgentHandler graphAgentHandler(ChatPort chatPort) {
        return new GraphAgentHandler(chatPort);
    }
}
