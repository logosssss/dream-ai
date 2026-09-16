package com.zhu.ai.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import com.zhu.ai.kernel.knowledge.RetrievePort;
import java.util.List;
import org.junit.jupiter.api.Test;

class HybridRetrievePortTest {

    @Test
    void fusesSameTextByWeightedSum() {
        RetrievePort lexical = (q, k) -> List.of(new RetrieveHit("kw", "same", 1.0, "kw", "intro"));
        RetrievePort dense = (q, k) -> List.of(new RetrieveHit("vec", "same", 1.0, "vec", "intro"));
        HybridRetrievePort hybrid = new HybridRetrievePort(lexical, dense, 0.6);
        List<RetrieveHit> hits = hybrid.retrieve("q", 3);
        assertEquals(1, hits.size());
        assertEquals("vec", hits.get(0).id());
        assertEquals(1.0, hits.get(0).score(), 1e-9);
    }

    @Test
    void keepsSideOnlyHitsScaled() {
        RetrievePort lexical = (q, k) -> List.of(new RetrieveHit("kw", "lexical-only", 1.0, "kw", "intro"));
        RetrievePort dense = (q, k) -> List.of(new RetrieveHit("vec", "dense-only", 1.0, "vec", "intro"));
        List<RetrieveHit> hits = new HybridRetrievePort(lexical, dense, 0.6).retrieve("q", 3);
        assertEquals(2, hits.size());
        assertEquals("vec", hits.get(0).id());
        assertEquals(0.6, hits.get(0).score(), 1e-9);
        assertEquals("kw", hits.get(1).id());
        assertEquals(0.4, hits.get(1).score(), 1e-9);
    }

    @Test
    void rejectsAlphaOutsideUnitInterval() {
        RetrievePort empty = (q, k) -> List.of();
        assertThrows(IllegalArgumentException.class, () -> new HybridRetrievePort(empty, empty, 1.1));
        assertThrows(IllegalArgumentException.class, () -> new HybridRetrievePort(empty, empty, -0.1));
    }

    @Test
    void emptySideReturnsTheOtherCapped() {
        RetrievePort lexical = (q, k) -> List.of(
                new RetrieveHit("a", "a", 1.0, "kw"),
                new RetrieveHit("b", "b", 0.9, "kw"),
                new RetrieveHit("c", "c", 0.8, "kw"));
        List<RetrieveHit> hits = new HybridRetrievePort(lexical, (q, k) -> List.of(), 0.6).retrieve("q", 2);
        assertEquals(2, hits.size());
        assertTrue(hits.get(0).id().equals("a"));
    }
}
