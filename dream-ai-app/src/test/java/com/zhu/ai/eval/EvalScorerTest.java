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
                new EvalExpect(
                        true,
                        "graph",
                        List.of("[route=chat]"),
                        List.of("[route=knowledge]"),
                        null,
                        2,
                        3,
                        "chat",
                        List.of(),
                        List.of()));
        AgentInvokeResult result = new AgentInvokeResult(
                "graph",
                "[route=chat]\nok",
                new InvokeObservation(
                        "t1", "s", "graph", 10, 1, 0, true, null, "chat", List.of(), List.of()));
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
                new EvalExpect(true, "graph", List.of("[route=knowledge]"), null, null, 0, null, null, null, null));
        AgentInvokeResult result = new AgentInvokeResult(
                "graph",
                "[route=chat]\nok",
                new InvokeObservation(
                        "t1", "s", "graph", 10, 1, 2, true, null, "chat", List.of(), List.of("current_date_time")));
        EvalCaseResult scored = EvalScorer.score(c, result);
        assertFalse(scored.passed());
        assertTrue(scored.failures().stream().anyMatch(f -> f.contains("output missing")));
        assertTrue(scored.failures().stream().anyMatch(f -> f.contains("toolCalls")));
    }

    @Test
    void assertsBlockedAndExecutedTools() {
        EvalCase c = new EvalCase(
                "tools",
                "chat",
                null,
                "x",
                new EvalExpect(
                        true,
                        "chat",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of("web_search_prime"),
                        List.of("datetime_offset")));
        AgentInvokeResult pass = new AgentInvokeResult(
                "chat",
                "ok",
                new InvokeObservation(
                        "t1",
                        "s",
                        "chat",
                        5,
                        2,
                        1,
                        true,
                        null,
                        "",
                        List.of("web_search_prime"),
                        List.of("datetime_offset")));
        assertTrue(EvalScorer.score(c, pass).passed());

        AgentInvokeResult fail = new AgentInvokeResult(
                "chat",
                "ok",
                new InvokeObservation(
                        "t2", "s", "chat", 5, 2, 0, true, null, "", List.of(), List.of()));
        EvalCaseResult scored = EvalScorer.score(c, fail);
        assertFalse(scored.passed());
        assertTrue(scored.failures().stream().anyMatch(f -> f.contains("blockedTools missing")));
        assertTrue(scored.failures().stream().anyMatch(f -> f.contains("executedTools missing")));
    }

    @Test
    void assertsObserveRoute() {
        EvalCase c = new EvalCase(
                "r1",
                "graph",
                null,
                "审查",
                new EvalExpect(true, "graph", null, null, null, null, null, "review", null, null));
        AgentInvokeResult wrong = new AgentInvokeResult(
                "graph",
                "[route=chat]\nx",
                new InvokeObservation("t", "s", "graph", 1, 1, 0, true, null, "chat", List.of(), List.of()));
        assertTrue(EvalScorer.score(c, wrong).failures().stream().anyMatch(f -> f.contains("route expected review")));
    }

    @Test
    void errorHelperMarksFailed() {
        EvalCase c = new EvalCase("e1", "chat", null, "x", EvalExpect.none());
        EvalCaseResult scored = EvalScorer.error(c, new IllegalArgumentException("boom"));
        assertFalse(scored.passed());
        assertEquals(List.of("invoke error: boom"), scored.failures());
    }
}
