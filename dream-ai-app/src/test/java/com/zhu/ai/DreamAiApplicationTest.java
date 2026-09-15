package com.zhu.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhu.ai.kernel.llm.ChatPort;
import org.junit.jupiter.api.Test;
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
@Import(DreamAiApplicationTest.StubChatConfig.class)
class DreamAiApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void invokeChatAgent() throws Exception {
        mockMvc.perform(post("/api/agent/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"chat\",\"sessionId\":\"t-hello\",\"input\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("chat"))
                .andExpect(jsonPath("$.output").value("stub:0:n:n:你好"));
    }

    @Test
    void sameSessionCarriesHistory() throws Exception {
        mockMvc.perform(post("/api/agent/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"chat\",\"sessionId\":\"sess-same\",\"input\":\"第一轮\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("stub:0:n:n:第一轮"));
        mockMvc.perform(post("/api/agent/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"chat\",\"sessionId\":\"sess-same\",\"input\":\"第二轮\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("stub:2:m:n:第二轮"));
    }

    @Test
    void differentSessionsAreIsolated() throws Exception {
        mockMvc.perform(post("/api/agent/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"chat\",\"sessionId\":\"sess-a\",\"input\":\"甲\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/agent/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"chat\",\"sessionId\":\"sess-b\",\"input\":\"乙\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("stub:0:n:n:乙"));
    }

    @Test
    void retrieveInjectsContext() throws Exception {
        mockMvc.perform(post("/api/agent/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"chat\",\"sessionId\":\"rag-http\",\"input\":\"什么是 AgentGateway\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("stub:0:n:r:什么是 AgentGateway"));
    }

    @Test
    void invokeIncludesObserveFields() throws Exception {
        mockMvc.perform(post("/api/agent/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"chat\",\"sessionId\":\"obs-http\",\"input\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.durationMs").isNumber())
                .andExpect(jsonPath("$.modelCalls").value(0))
                .andExpect(jsonPath("$.toolCalls").value(0));
        mockMvc.perform(get("/api/observe?limit=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("obs-http"))
                .andExpect(jsonPath("$[0].success").value(true));
    }

    @Test
    void invokeStreamEmitsDeltaAndDone() throws Exception {
        var mvcResult = mockMvc.perform(post("/api/agent/invoke/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"agentId\":\"chat\",\"sessionId\":\"sse-hello\",\"input\":\"hello\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:delta")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("stub:0:n:n:hello")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));
    }

    @Test
    void unknownAgentIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/agent/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"nope\",\"sessionId\":\"s1\",\"input\":\"你好\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unknown agent: nope"));
    }

    @Test
    void blankInputIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/agent/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"chat\",\"sessionId\":\"s1\",\"input\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("input required"));
    }

    @TestConfiguration
    static class StubChatConfig {

        @Bean
        ChatPort chatPort() {
            return request -> {
                boolean remembered = request.system() != null && request.system().contains("长期记忆");
                boolean rag = request.system() != null
                        && request.system().contains("参考资料（知识问题优先依据这些片段");
                return "stub:"
                        + request.history().size()
                        + ":"
                        + (remembered ? "m" : "n")
                        + ":"
                        + (rag ? "r" : "n")
                        + ":"
                        + request.prompt();
            };
        }
    }
}
