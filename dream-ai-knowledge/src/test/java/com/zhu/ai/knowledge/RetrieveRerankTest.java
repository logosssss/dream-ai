package com.zhu.ai.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrieveRerankTest {

    @Test
    void prefersKeywordOverlapOverLongHighScoreNoise() {
        RetrieveHit noise = new RetrieveHit(
                "long",
                "这段很长但和查询无关的填充文字。".repeat(20),
                0.99,
                "s",
                "intro");
        RetrieveHit match = new RetrieveHit("short", "HTTP 只进 AgentGateway。", 0.4, "s", "intro");
        List<RetrieveHit> ranked = RetrieveRerank.rerank("AgentGateway", List.of(noise, match), 2);
        assertEquals("short", ranked.get(0).id());
        assertTrue(ranked.get(0).score() > ranked.get(1).score());
    }

    @Test
    void capsAtTopK() {
        List<RetrieveHit> hits = List.of(
                new RetrieveHit("a", "AgentGateway 入口", 0.5, "s"),
                new RetrieveHit("b", "AgentGateway HTTP", 0.4, "s"),
                new RetrieveHit("c", "无关", 0.3, "s"));
        assertEquals(1, RetrieveRerank.rerank("AgentGateway", hits, 1).size());
    }
}
