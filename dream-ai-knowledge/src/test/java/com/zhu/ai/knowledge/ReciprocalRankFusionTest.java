package com.zhu.ai.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {

    @Test
    void prefersSharedTopRanks() {
        ScoreFusion fusion = new ReciprocalRankFusion(60);
        List<RetrieveHit> out = fusion.fuse(
                List.of(
                        new ScoreFusion.ChannelHits(
                                "kw",
                                List.of(
                                        new RetrieveHit("shared", "same", 0.1, "kw"),
                                        new RetrieveHit("only-kw", "kw-only", 0.9, "kw")),
                                1.0),
                        new ScoreFusion.ChannelHits(
                                "vec",
                                List.of(
                                        new RetrieveHit("shared", "same", 0.1, "vec"),
                                        new RetrieveHit("only-vec", "vec-only", 0.9, "vec")),
                                1.0)),
                3);
        assertEquals("same", out.get(0).text());
        assertTrue(out.get(0).score() > out.get(1).score());
    }

    @Test
    void scoreFusionsFactory() {
        assertTrue(ScoreFusions.create("linear") instanceof LinearWeightedFusion);
        assertTrue(ScoreFusions.create("rrf") instanceof ReciprocalRankFusion);
        assertThrows(IllegalArgumentException.class, () -> ScoreFusions.create("nope"));
    }
}
