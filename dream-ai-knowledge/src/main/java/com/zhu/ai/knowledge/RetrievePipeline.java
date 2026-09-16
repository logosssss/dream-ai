package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import com.zhu.ai.kernel.knowledge.RetrievePort;
import java.util.List;

/**
 * 召回加宽 → 可选 docType 过滤 → rerank 截断到 topK。
 */
public final class RetrievePipeline implements RetrievePort {

    private final RetrievePort inner;

    private final String docType;

    private final boolean rerank;

    public RetrievePipeline(RetrievePort inner, String docType, boolean rerank) {
        this.inner = inner;
        this.docType = docType == null ? "" : docType.trim();
        this.rerank = rerank;
    }

    @Override
    public List<RetrieveHit> retrieve(String query, int topK) {
        if (inner == null || topK <= 0) {
            return List.of();
        }
        int recall = Math.max(topK * 3, topK);
        List<RetrieveHit> hits = inner.retrieve(query, recall);
        hits = RetrieveFilters.byDocType(hits, docType);
        if (rerank) {
            return RetrieveRerank.rerank(query, hits, topK);
        }
        if (hits.size() <= topK) {
            return hits;
        }
        return List.copyOf(hits.subList(0, topK));
    }
}
