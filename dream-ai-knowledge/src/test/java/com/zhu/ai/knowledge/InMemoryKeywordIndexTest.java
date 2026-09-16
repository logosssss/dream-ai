package com.zhu.ai.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.knowledge.RetrieveCitations;
import com.zhu.ai.kernel.knowledge.RetrieveHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryKeywordIndexTest {

    @Test
    void retrievesMatchingChunk() {
        InMemoryKeywordIndex index = new InMemoryKeywordIndex();
        index.ingest("HTTP 只进 AgentGateway。\n\n检索在进程内，不必先上 8096。");
        List<RetrieveHit> hits = index.retrieve("8096 是什么", 3);
        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).text().contains("8096"));
        assertTrue(hits.get(0).id().startsWith("kw-"));
        assertTrue(hits.get(0).score() > 0);
        assertEquals("in-memory", hits.get(0).source());
        assertTrue(RetrieveCitations.format(hits).startsWith("[1]"));
    }

    @Test
    void missesUnrelatedQuery() {
        InMemoryKeywordIndex index = new InMemoryKeywordIndex();
        index.ingest("HTTP 只进 AgentGateway。");
        assertTrue(index.retrieve("量子纠缠证明", 3).isEmpty());
    }

    @Test
    void minScoreFiltersWeakOverlap() {
        InMemoryKeywordIndex strict = new InMemoryKeywordIndex(0.9);
        strict.ingest("检索在进程内，不必先上 8096。");
        assertTrue(strict.retrieve("8096 是什么", 3).isEmpty());
        InMemoryKeywordIndex loose = new InMemoryKeywordIndex(0.0);
        loose.ingest("检索在进程内，不必先上 8096。");
        assertFalse(loose.retrieve("8096 是什么", 3).isEmpty());
    }

    @Test
    void ingestKeepsDocType() {
        InMemoryKeywordIndex index = new InMemoryKeywordIndex();
        index.ingest("HTTP 只进 AgentGateway。", "classpath:intro", "intro");
        List<RetrieveHit> hits = index.retrieve("AgentGateway", 1);
        assertEquals(1, hits.size());
        assertEquals("intro", hits.get(0).docType());
        assertEquals("classpath:intro", hits.get(0).source());
    }
}
