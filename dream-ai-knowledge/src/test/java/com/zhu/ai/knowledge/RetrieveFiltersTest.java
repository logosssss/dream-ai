package com.zhu.ai.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrieveFiltersTest {

    @Test
    void blankDocTypeKeepsAll() {
        List<RetrieveHit> hits = List.of(
                new RetrieveHit("a", "intro", 1.0, "s", "intro"),
                new RetrieveHit("b", "api", 0.9, "s", "api"));
        assertEquals(2, RetrieveFilters.byDocType(hits, "").size());
        assertEquals(2, RetrieveFilters.byDocType(hits, null).size());
    }

    @Test
    void keepsMatchingDocTypeIgnoreCase() {
        List<RetrieveHit> hits = List.of(
                new RetrieveHit("a", "intro", 1.0, "s", "intro"),
                new RetrieveHit("b", "api", 0.9, "s", "api"));
        List<RetrieveHit> filtered = RetrieveFilters.byDocType(hits, "INTRO");
        assertEquals(1, filtered.size());
        assertEquals("a", filtered.get(0).id());
    }

    @Test
    void emptyWhenNoMatch() {
        List<RetrieveHit> hits = List.of(new RetrieveHit("a", "intro", 1.0, "s", "intro"));
        assertTrue(RetrieveFilters.byDocType(hits, "api").isEmpty());
    }
}
