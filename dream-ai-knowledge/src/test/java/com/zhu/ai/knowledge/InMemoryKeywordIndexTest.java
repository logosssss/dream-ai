package com.zhu.ai.knowledge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryKeywordIndexTest {

    @Test
    void retrievesMatchingChunk() {
        InMemoryKeywordIndex index = new InMemoryKeywordIndex();
        index.ingest("HTTP 只进 AgentGateway。\n\n检索在进程内，不必先上 8096。");
        List<String> hits = index.retrieve("8096 是什么", 3);
        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).contains("8096"));
    }

    @Test
    void missesUnrelatedQuery() {
        InMemoryKeywordIndex index = new InMemoryKeywordIndex();
        index.ingest("HTTP 只进 AgentGateway。");
        assertTrue(index.retrieve("量子纠缠证明", 3).isEmpty());
    }
}
