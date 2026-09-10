package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrievePort;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * 用 Spring AI {@link VectorStore}（pgvector）实现 {@link RetrievePort}。
 * 厂商与 JDBC 留在 app；Agent 仍只看见 Port。
 */
public final class PgVectorRetrievePort implements RetrievePort {

    private final VectorStore vectorStore;

    public PgVectorRetrievePort(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<String> retrieve(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).similarityThreshold(0.0).build());
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<String> texts = new ArrayList<>(hits.size());
        for (Document hit : hits) {
            String text = hit.getText();
            if (text != null && !text.isBlank()) {
                texts.add(text);
            }
        }
        return List.copyOf(texts);
    }
}
