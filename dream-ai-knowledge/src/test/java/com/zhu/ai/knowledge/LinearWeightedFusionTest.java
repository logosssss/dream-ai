package com.zhu.ai.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinearWeightedFusionTest {

    @Test
    void fusesSameTextByWeightedSum() {
        ScoreFusion fusion = new LinearWeightedFusion();
        List<RetrieveHit> out = fusion.fuse(
                List.of(
                        new ScoreFusion.ChannelHits(
                                "kw", List.of(new RetrieveHit("kw", "same", 1.0, "kw", "intro")), 0.4),
                        new ScoreFusion.ChannelHits(
                                "vec", List.of(new RetrieveHit("vec", "same", 1.0, "vec", "intro")), 0.6)),
                3);
        assertEquals(1, out.size());
        assertEquals("vec", out.get(0).id());
        assertEquals(1.0, out.get(0).score(), 1e-9);
    }

    @Test
    void singleNonEmptyChannelKeepsRawScores() {
        ScoreFusion fusion = new LinearWeightedFusion();
        List<RetrieveHit> out = fusion.fuse(
                List.of(
                        new ScoreFusion.ChannelHits(
                                "kw",
                                List.of(
                                        new RetrieveHit("a", "a", 1.0, "kw"),
                                        new RetrieveHit("b", "b", 0.9, "kw"),
                                        new RetrieveHit("c", "c", 0.8, "kw")),
                                0.4),
                        new ScoreFusion.ChannelHits("vec", List.of(), 0.6)),
                2);
        assertEquals(2, out.size());
        assertEquals("a", out.get(0).id());
        assertEquals(1.0, out.get(0).score(), 1e-9);
    }

    @Test
    void rejectsNothingButEmptyLimit() {
        assertEquals(
                List.of(),
                new LinearWeightedFusion()
                        .fuse(List.of(new ScoreFusion.ChannelHits("a", List.of(), 1.0)), 0));
    }
}
