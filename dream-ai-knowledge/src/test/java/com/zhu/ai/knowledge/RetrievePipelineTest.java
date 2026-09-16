package com.zhu.ai.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import com.zhu.ai.kernel.knowledge.RetrievePort;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievePipelineTest {

    @Test
    void filtersThenReranksToTopK() {
        RetrievePort inner = (q, k) -> List.of(
                new RetrieveHit("noise", "无关长文".repeat(30), 0.99, "s", "intro"),
                new RetrieveHit("hit", "HTTP 只进 AgentGateway。", 0.4, "s", "intro"),
                new RetrieveHit("api", "AgentGateway REST", 0.95, "s", "api"));
        RetrievePipeline pipeline = new RetrievePipeline(inner, "intro", true);
        List<RetrieveHit> hits = pipeline.retrieve("AgentGateway", 1);
        assertEquals(1, hits.size());
        assertEquals("hit", hits.get(0).id());
        assertEquals("intro", hits.get(0).docType());
    }

    @Test
    void withoutRerankTruncatesAfterFilter() {
        RetrievePort inner = (q, k) -> List.of(
                new RetrieveHit("a", "one", 0.9, "s", "intro"),
                new RetrieveHit("b", "two", 0.8, "s", "intro"),
                new RetrieveHit("c", "three", 0.7, "s", "api"));
        List<RetrieveHit> hits = new RetrievePipeline(inner, "intro", false).retrieve("q", 1);
        assertEquals(1, hits.size());
        assertEquals("a", hits.get(0).id());
    }

    @Test
    void blankDocTypeDoesNotFilter() {
        RetrievePort inner = (q, k) -> List.of(
                new RetrieveHit("a", "intro", 1.0, "s", "intro"),
                new RetrieveHit("b", "api", 0.9, "s", "api"));
        List<RetrieveHit> hits = new RetrievePipeline(inner, "", false).retrieve("q", 2);
        assertEquals(2, hits.size());
    }

    @Test
    void emptyWhenDocTypeMisses() {
        RetrievePort inner = (q, k) -> List.of(new RetrieveHit("a", "intro", 1.0, "s", "intro"));
        assertTrue(new RetrievePipeline(inner, "api", true).retrieve("q", 3).isEmpty());
    }
}
