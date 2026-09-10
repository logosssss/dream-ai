package com.zhu.ai.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class PgVectorRetrievePortTest {

    @Test
    void mapsHitTexts() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("HTTP 只进 AgentGateway。")));
        PgVectorRetrievePort port = new PgVectorRetrievePort(store);
        List<String> hits = port.retrieve("什么是 AgentGateway", 3);
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).contains("AgentGateway"));
    }

    @Test
    void blankQueryDoesNotSearch() {
        VectorStore store = mock(VectorStore.class);
        PgVectorRetrievePort port = new PgVectorRetrievePort(store);
        assertTrue(port.retrieve("  ", 3).isEmpty());
        verify(store, never()).similaritySearch(any(SearchRequest.class));
    }
}
