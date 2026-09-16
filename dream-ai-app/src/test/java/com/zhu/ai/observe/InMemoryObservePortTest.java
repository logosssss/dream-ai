package com.zhu.ai.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.knowledge.RetrieveHitSummary;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryObservePortTest {

    @Test
    void recordsRouteBlockedAndExecutedTools() {
        InMemoryObservePort observe = new InMemoryObservePort();
        observe.begin("s1", "graph");
        observe.markModelCall();
        observe.markRoute("review");
        observe.markModel("qwen-max");
        observe.markToolBlocked("web_search_prime");
        observe.markToolBlocked("web_search_prime");
        observe.markToolExecuted("datetime_offset");
        observe.markRetrieveHits(List.of(new RetrieveHitSummary("kw-1", 0.5, "in-memory")));

        var obs = observe.complete(true, null);
        assertEquals("review", obs.route());
        assertEquals("qwen-max", obs.model());
        assertEquals(List.of("web_search_prime"), obs.blockedTools());
        assertEquals(List.of("datetime_offset"), obs.executedTools());
        assertEquals(1, obs.modelCalls());
        assertEquals(1, obs.toolCalls());
        assertEquals(1, obs.retrieveHits().size());
        assertEquals("kw-1", obs.retrieveHits().getFirst().id());
        assertTrue(obs.success());
        assertEquals(obs.traceId(), observe.recent(1).getFirst().traceId());
    }
}
