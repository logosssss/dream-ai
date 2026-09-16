package com.zhu.ai.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhu.ai.kernel.eval.EvalCase;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClasspathEvalCasesTest {

    @Test
    void loadsClasspathFixtures() {
        List<EvalCase> cases = new ClasspathEvalCases(new ObjectMapper()).load();
        assertEquals(5, cases.size());
        assertEquals("chat-hello", cases.get(0).id());
        assertEquals("graph-knowledge-miss", cases.get(1).id());
        assertEquals("graph-route-chat", cases.get(2).id());
        assertEquals("graph-route-knowledge", cases.get(3).id());
        assertEquals("graph-route-review", cases.get(4).id());
        assertEquals(0, cases.get(0).expect().maxRetrieveHits());
        assertEquals(0, cases.get(1).expect().maxRetrieveHits());
        assertEquals(0, cases.get(1).expect().maxModelCalls());
        assertTrue(cases.get(1).expect().outputContains().contains("资料不足"));
        assertEquals(0, cases.get(2).expect().maxRetrieveHits());
        assertTrue(cases.get(3).expect().outputContains().contains("[route=knowledge]"));
        assertTrue(cases.get(3).expect().outputContains().contains("[1]"));
        assertEquals(1, cases.get(3).expect().minRetrieveHits());
        assertTrue(cases.get(4).expect().outputContains().contains("[route=review]"));
        assertEquals("review", cases.get(4).expect().route());
        assertEquals(0, cases.get(4).expect().maxRetrieveHits());
    }
}
