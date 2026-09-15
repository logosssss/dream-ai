package com.zhu.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhu.ai.eval.ClasspathEvalCases;
import com.zhu.ai.eval.GatewayEvalRunner;
import com.zhu.ai.kernel.runtime.AgentGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 评测回归组合根：classpath cases + Gateway runner。 */
@Configuration
public class EvalConfig {

    @Bean
    ClasspathEvalCases classpathEvalCases(ObjectMapper objectMapper) {
        return new ClasspathEvalCases(objectMapper);
    }

    @Bean
    GatewayEvalRunner gatewayEvalRunner(AgentGateway agentGateway, ClasspathEvalCases classpathEvalCases) {
        return new GatewayEvalRunner(agentGateway, classpathEvalCases);
    }
}
