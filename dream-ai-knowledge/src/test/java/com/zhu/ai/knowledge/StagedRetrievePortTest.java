package com.zhu.ai.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import com.zhu.ai.kernel.knowledge.RetrievePort;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StagedRetrievePortTest {

    @Test
    void filtersThenReranksToTopK() {
        RetrievePort inner = (q, k) -> List.of(
                new RetrieveHit("noise", "无关长文".repeat(30), 0.99, "s", "intro"),
                new RetrieveHit("hit", "HTTP 只进 AgentGateway。", 0.4, "s", "intro"),
                new RetrieveHit("api", "AgentGateway REST", 0.95, "s", "api"));
        RetrievePort pipeline = RetrieveStages.pipeline(
                List.of(new MultiSourceRecallStage.WeightedSource("inner", inner, 1.0)),
                null,
                "intro",
                true);
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
        List<RetrieveHit> hits = RetrieveStages.pipeline(
                        List.of(new MultiSourceRecallStage.WeightedSource("inner", inner, 1.0)),
                        null,
                        "intro",
                        false)
                .retrieve("q", 1);
        assertEquals(1, hits.size());
        assertEquals("a", hits.get(0).id());
    }

    @Test
    void blankDocTypeDoesNotFilter() {
        RetrievePort inner = (q, k) -> List.of(
                new RetrieveHit("a", "intro", 1.0, "s", "intro"),
                new RetrieveHit("b", "api", 0.9, "s", "api"));
        List<RetrieveHit> hits = RetrieveStages.pipeline(
                        List.of(new MultiSourceRecallStage.WeightedSource("inner", inner, 1.0)),
                        null,
                        "",
                        false)
                .retrieve("q", 2);
        assertEquals(2, hits.size());
    }

    @Test
    void emptyWhenDocTypeMisses() {
        RetrievePort inner = (q, k) -> List.of(new RetrieveHit("a", "intro", 1.0, "s", "intro"));
        assertTrue(RetrieveStages.pipeline(
                        List.of(new MultiSourceRecallStage.WeightedSource("inner", inner, 1.0)),
                        null,
                        "api",
                        true)
                .retrieve("q", 3)
                .isEmpty());
    }

    @Test
    void explicitStageNamesAndExtraStage() {
        RetrievePort inner = (q, k) -> List.of(
                new RetrieveHit("a", "keep", 1.0, "s", "intro"),
                new RetrieveHit("b", "drop", 0.9, "s", "api"));
        AtomicReference<Integer> seen = new AtomicReference<>(0);
        RetrieveStage marker = RetrieveStage.named("marker", (ctx, input) -> {
            seen.set(input.size());
            return input;
        });
        RetrievePort port = RetrieveStages.pipeline(
                List.of(new MultiSourceRecallStage.WeightedSource("inner", inner, 1.0)),
                ScoreFusions.create("linear"),
                RetrieveOptions.ofDocType("intro"),
                List.of("recall", "filter", "truncate"),
                List.of(marker));
        List<RetrieveHit> hits = port.retrieve("q", 2);
        assertEquals(1, hits.size());
        assertEquals("a", hits.get(0).id());
        assertEquals(1, seen.get());
    }

    @Test
    void stageFailurePropagatesWithStageName() {
        RetrieveStage boom = RetrieveStage.named("boom", (ctx, input) -> {
            throw new IllegalStateException("explode");
        });
        StagedRetrievePort port = new StagedRetrievePort(List.of(boom));
        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> port.retrieve("q", 1));
        assertEquals("explode", ex.getMessage());
    }

    @Test
    void tracesIncludeOutChars() {
        RetrievePort inner = (q, k) -> List.of(new RetrieveHit("a", "abcd", 1.0, "s", "intro"));
        StagedRetrievePort port = new StagedRetrievePort(
                List.of(
                        new MultiSourceRecallStage(
                                List.of(new MultiSourceRecallStage.WeightedSource("inner", inner, 1.0)),
                                new LinearWeightedFusion(),
                                1),
                        new TruncateStage()),
                RetrieveOptions.ofDocType("intro"));
        List<RetrieveHit> hits = port.retrieve("q", 1);
        assertEquals(1, hits.size());
        assertEquals(4, StagedRetrievePort.sumChars(hits));
    }
}
