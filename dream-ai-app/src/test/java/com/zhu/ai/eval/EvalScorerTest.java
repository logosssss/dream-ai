package com.zhu.ai.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.eval.EvalCase;
import com.zhu.ai.kernel.eval.EvalCaseResult;
import com.zhu.ai.kernel.eval.EvalExpect;
import com.zhu.ai.kernel.eval.EvalScorer;
import com.zhu.ai.kernel.observe.InvokeObservation;
import com.zhu.ai.kernel.runtime.AgentInvokeResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvalScorerTest {

    @Test
    void passesWhenContainsAndRouteMatch() {
        EvalCase c = new EvalCase(
                "g1",
                "graph",
                null,
                "hi",
                new EvalExpect(true, "graph", List.of("[route=chat]"), List.of("[route=knowledge]"), null, 2, 3));
        AgentInvokeResult result = new AgentInvokeResult(
                "graph",
                "[route=chat]\nok",
                new InvokeObservation("t1", "s", "graph", 10, 1, 0, true, null));
        EvalCaseResult scored = EvalScorer.score(c, result);
        assertTrue(scored.passed());
        assertTrue(scored.failures().isEmpty());
    }

    @Test
    void failsOnMissingNeedleAndToolBudget() {
        EvalCase c = new EvalCase(
                "g2",
                "graph",
                null,
                "hi",
                new EvalExpect(true, "graph", List.of("[route=knowledge]"), null, null, 0, null));
        AgentInvokeResult result = new AgentInvokeResult(
                "graph",
                "[route=chat]\nok",
                new InvokeObservation("t1", "s", "graph", 10, 1, 2, true, null));
        EvalCaseResult scored = EvalScorer.score(c, result);
        assertFalse(scored.passed());
        assertTrue(scored.failures().stream().anyMatch(f -> f.contains("output missing")));
        assertTrue(scored.failures().stream().anyMatch(f -> f.contains("toolCalls")));
    }

    @Test
    void errorHelperMarksFailed() {
        EvalCase c = new EvalCase("e1", "chat", null, "x", new EvalExpect(null, null, null, null, null, null, null));
        EvalCaseResult scored = EvalScorer.error(c, new IllegalArgumentException("boom"));
        assertFalse(scored.passed());
        assertEquals(List.of("invoke error: boom"), scored.failures());
    }
}
