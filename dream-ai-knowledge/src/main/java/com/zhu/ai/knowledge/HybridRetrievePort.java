package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import com.zhu.ai.kernel.knowledge.RetrievePort;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 关键词 + 向量融合。同一正文合并：{@code α * 向量分 + (1-α) * 关键词分}。
 * 缺一侧当 0。ids 不同时以正文为键。
 */
public final class HybridRetrievePort implements RetrievePort {

    private final RetrievePort lexical;

    private final RetrievePort dense;

    private final double vectorWeight;

    public HybridRetrievePort(RetrievePort lexical, RetrievePort dense, double vectorWeight) {
        this.lexical = lexical;
        this.dense = dense;
        if (vectorWeight < 0.0 || vectorWeight > 1.0) {
            throw new IllegalArgumentException("vectorWeight must be in [0, 1]");
        }
        this.vectorWeight = vectorWeight;
    }

    @Override
    public List<RetrieveHit> retrieve(String query, int topK) {
        if (topK <= 0) {
            return List.of();
        }
        List<RetrieveHit> kw = lexical == null ? List.of() : lexical.retrieve(query, topK);
        List<RetrieveHit> vec = dense == null ? List.of() : dense.retrieve(query, topK);
        if (kw.isEmpty()) {
            return cap(vec, topK);
        }
        if (vec.isEmpty()) {
            return cap(kw, topK);
        }
        Map<String, RetrieveHit> byText = new LinkedHashMap<>();
        for (RetrieveHit hit : kw) {
            byText.put(hit.text(), scale(hit, 1.0 - vectorWeight));
        }
        for (RetrieveHit hit : vec) {
            RetrieveHit existing = byText.get(hit.text());
            if (existing == null) {
                byText.put(hit.text(), scale(hit, vectorWeight));
            } else {
                byText.put(hit.text(), merge(existing, hit));
            }
        }
        return byText.values().stream()
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(topK)
                .toList();
    }

    private RetrieveHit scale(RetrieveHit hit, double weight) {
        return new RetrieveHit(hit.id(), hit.text(), hit.score() * weight, hit.source(), hit.docType());
    }

    private RetrieveHit merge(RetrieveHit lexicalHit, RetrieveHit denseHit) {
        double fused = lexicalHit.score() + denseHit.score() * vectorWeight;
        String id = denseHit.id().isBlank() ? lexicalHit.id() : denseHit.id();
        String source = denseHit.source().isBlank() ? lexicalHit.source() : denseHit.source();
        String docType = denseHit.docType().isBlank() ? lexicalHit.docType() : denseHit.docType();
        return new RetrieveHit(id, denseHit.text(), fused, source, docType);
    }

    private static List<RetrieveHit> cap(List<RetrieveHit> hits, int topK) {
        if (hits.size() <= topK) {
            return hits;
        }
        return List.copyOf(hits.subList(0, topK));
    }
}
