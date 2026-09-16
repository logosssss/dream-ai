package com.zhu.ai.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryObservePortTest {

    @Test
    void recordsRouteBlockedAndExecutedTools() {
        InMemoryObservePort observe = new InMemoryObservePort();
        observe.begin("s1", "graph");
        observe.markModelCall();
        observe.markRoute("review");
        observe.markToolBlocked("web_search_prime");
        observe.markToolBlocked("web_search_prime");
        observe.markToolExecuted("datetime_offset");

        var obs = observe.complete(true, null);
        assertEquals("review", obs.route());
        assertEquals(List.of("web_search_prime"), obs.blockedTools());
        assertEquals(List.of("datetime_offset"), obs.executedTools());
        assertEquals(1, obs.modelCalls());
        assertEquals(1, obs.toolCalls());
        assertTrue(obs.success());
        assertEquals(obs.traceId(), observe.recent(1).getFirst().traceId());
    }
}
