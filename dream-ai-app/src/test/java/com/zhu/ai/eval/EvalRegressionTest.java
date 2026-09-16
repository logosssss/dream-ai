package com.zhu.ai.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhu.ai.kernel.eval.EvalSuiteResult;
import com.zhu.ai.kernel.llm.ChatPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EvalRegressionTest.StubChatConfig.class)
class EvalRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GatewayEvalRunner runner;

    @Test
    void goldenSuiteAllPassesWithStubChat() {
        EvalSuiteResult suite = runner.runAll();
        assertEquals(5, suite.total());
        assertTrue(suite.allPassed(), () -> "failures=" + suite.results());
    }

    @Test
    void httpListsAndRunsSuite() throws Exception {
        mockMvc.perform(get("/api/eval/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].id").value("chat-hello"));

        mockMvc.perform(post("/api/eval/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.passed").value(5));
    }

    @TestConfiguration
    static class StubChatConfig {

        @Bean
        ChatPort chatPort() {
            return request -> "stub:" + request.prompt();
        }
    }
}
