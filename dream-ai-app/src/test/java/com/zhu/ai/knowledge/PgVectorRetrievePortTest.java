package com.zhu.ai.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class PgVectorRetrievePortTest {

    @Test
    void mapsHitFields() {
        VectorStore store = mock(VectorStore.class);
        Document doc = new Document(
                "id-1", "HTTP 只进 AgentGateway。", Map.of("source", "intro", "docType", "intro"));
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        PgVectorRetrievePort port = new PgVectorRetrievePort(store, 0.2);
        List<RetrieveHit> hits = port.retrieve("什么是 AgentGateway", 3);
        assertEquals(1, hits.size());
        assertEquals("id-1", hits.get(0).id());
        assertTrue(hits.get(0).text().contains("AgentGateway"));
        assertEquals("intro", hits.get(0).source());
        assertEquals("intro", hits.get(0).docType());
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(store).similaritySearch(captor.capture());
        assertEquals(0.2, captor.getValue().getSimilarityThreshold(), 1e-9);
        assertEquals(3, captor.getValue().getTopK());
    }

    @Test
    void blankQueryDoesNotSearch() {
        VectorStore store = mock(VectorStore.class);
        PgVectorRetrievePort port = new PgVectorRetrievePort(store);
        assertTrue(port.retrieve("  ", 3).isEmpty());
        verify(store, never()).similaritySearch(any(SearchRequest.class));
    }
}
