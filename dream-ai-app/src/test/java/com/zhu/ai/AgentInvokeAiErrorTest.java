package com.zhu.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhu.ai.kernel.llm.ChatPort;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AgentInvokeAiErrorTest.ThrowingChatConfig.class)
class AgentInvokeAiErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void nonTransientAiIsBadGateway() throws Exception {
        mockMvc.perform(post("/api/agent/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"chat\",\"sessionId\":\"s1\",\"input\":\"boom\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("HTTP 404 - No response body available"));
    }

    @Test
    void transientAiIsServiceUnavailable() throws Exception {
        mockMvc.perform(post("/api/agent/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"chat\",\"sessionId\":\"s1\",\"input\":\"timeout\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("timeout"));
    }

    @TestConfiguration
    static class ThrowingChatConfig {

        @Bean
        ChatPort chatPort() {
            return request -> {
                if ("timeout".equals(request.prompt())) {
                    throw new TransientAiException("timeout");
                }
                throw new NonTransientAiException("HTTP 404 - No response body available");
            };
        }
    }
}
