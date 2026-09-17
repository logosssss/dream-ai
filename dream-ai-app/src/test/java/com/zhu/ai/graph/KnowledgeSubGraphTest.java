package com.zhu.ai.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class KnowledgeSubGraphTest {

    @Test
    void gateEmptyRefusesWithoutModel() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CompiledGraph graph = KnowledgeSubGraph.compile(
                request -> {
                    calls.incrementAndGet();
                    return "should-not";
                },
                task -> null,
                null,
                List.of(),
                null);
        Optional<OverAllState> done = graph.invoke(seed("问知识库", ""));
        assertTrue(done.isPresent());
        assertEquals(KnowledgeSubGraph.GATE_EMPTY, done.get().value(SupervisorKeys.KNOWLEDGE_GATE).orElse(""));
        assertEquals(KnowledgeSubGraph.NO_HIT_REPLY, done.get().value(SupervisorKeys.OUTPUT).orElse(""));
        assertEquals(0, calls.get());
    }

    @Test
    void gateOkGeneratesAndCites() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CompiledGraph graph = KnowledgeSubGraph.compile(
                request -> {
                    calls.incrementAndGet();
                    assertTrue(request.system().contains("知识问答助手"));
                    return "答案正文";
                },
                task -> null,
                null,
                List.of(),
                null);
        Optional<OverAllState> done = graph.invoke(seed("问知识库", "[1] 资料A"));
        assertTrue(done.isPresent());
        assertEquals(KnowledgeSubGraph.GATE_OK, done.get().value(SupervisorKeys.KNOWLEDGE_GATE).orElse(""));
        assertEquals("答案正文" + KnowledgeSubGraph.CITATION_FALLBACK, done.get().value(SupervisorKeys.OUTPUT).orElse(""));
        assertEquals(1, calls.get());
    }

    @Test
    void ensureCitationsIdempotent() {
        assertEquals("见 [1]", KnowledgeSubGraph.ensureCitations("见 [1]", "[1] x"));
        assertFalse(KnowledgeSubGraph.ensureCitations("无编号", "无标记").contains("依据"));
    }

    private static Map<String, Object> seed(String input, String retrieved) {
        Map<String, Object> seed = new HashMap<>();
        seed.put(SupervisorKeys.INPUT, input);
        seed.put(SupervisorKeys.MEMORY, "");
        seed.put(SupervisorKeys.RETRIEVED, retrieved);
        seed.put(SupervisorKeys.ROUTE, IntentRouter.KNOWLEDGE);
        return seed;
    }
}
