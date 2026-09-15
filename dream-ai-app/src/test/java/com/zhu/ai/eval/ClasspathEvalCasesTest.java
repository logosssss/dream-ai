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
        assertEquals(3, cases.size());
        assertEquals("chat-hello", cases.get(0).id());
        assertEquals("graph-route-chat", cases.get(1).id());
        assertEquals("graph-route-knowledge", cases.get(2).id());
        assertTrue(cases.get(2).expect().outputContains().contains("[route=knowledge]"));
    }
}
