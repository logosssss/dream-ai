package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import com.zhu.ai.kernel.knowledge.RetrievePort;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * 用 Spring AI {@link VectorStore}（pgvector）实现 {@link RetrievePort}。
 * 厂商与 JDBC 留在 app；Agent 仍只看见 Port，不注入 VectorStore。
 */
public final class PgVectorRetrievePort implements RetrievePort {

    private final VectorStore vectorStore;

    private final double minScore;

    public PgVectorRetrievePort(VectorStore vectorStore) {
        this(vectorStore, 0.0);
    }

    public PgVectorRetrievePort(VectorStore vectorStore, double minScore) {
        if (minScore < 0.0 || minScore > 1.0) {
            throw new IllegalArgumentException("minScore must be in [0, 1]");
        }
        this.vectorStore = vectorStore;
        this.minScore = minScore;
    }

    @Override
    public List<RetrieveHit> retrieve(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(minScore)
                        .build());
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        List<RetrieveHit> hits = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            String text = doc.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            double score = doc.getScore() == null ? 0.0 : doc.getScore();
            Object source = doc.getMetadata() == null ? null : doc.getMetadata().get("source");
            Object docType = doc.getMetadata() == null ? null : doc.getMetadata().get("docType");
            hits.add(new RetrieveHit(
                    doc.getId() == null ? "" : doc.getId(),
                    text,
                    score,
                    source == null ? "" : String.valueOf(source),
                    docType == null ? "" : String.valueOf(docType)));
        }
        return List.copyOf(hits);
    }
}
