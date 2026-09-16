package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 二次排序：原分 + 关键词重叠 − 过长惩罚。召回可以宽，展示要准。
 */
public final class RetrieveRerank {

    static final int SOFT_LEN = 280;

    private RetrieveRerank() {}

    public static List<RetrieveHit> rerank(String query, List<RetrieveHit> hits, int topK) {
        if (hits == null || hits.isEmpty() || topK <= 0) {
            return List.of();
        }
        Set<String> terms = InMemoryKeywordIndex.terms(query == null ? "" : query);
        record Row(RetrieveHit hit, double fused) {}
        List<Row> rows = new ArrayList<>(hits.size());
        for (RetrieveHit hit : hits) {
            double keyword = keywordOverlap(hit.text(), terms);
            double brevity = 1.0 / (1.0 + hit.text().length() / (double) SOFT_LEN);
            double fused = 0.5 * clamp01(hit.score()) + 0.35 * keyword + 0.15 * brevity;
            rows.add(new Row(hit, fused));
        }
        rows.sort(Comparator.comparingDouble(Row::fused).reversed());
        int limit = Math.min(topK, rows.size());
        List<RetrieveHit> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            RetrieveHit hit = rows.get(i).hit();
            out.add(new RetrieveHit(hit.id(), hit.text(), rows.get(i).fused(), hit.source(), hit.docType()));
        }
        return List.copyOf(out);
    }

    static double keywordOverlap(String text, Set<String> terms) {
        if (terms.isEmpty() || text == null || text.isBlank()) {
            return 0.0;
        }
        String hay = text.toLowerCase(Locale.ROOT);
        int matched = 0;
        for (String term : terms) {
            if (hay.contains(term)) {
                matched++;
            }
        }
        return matched / (double) terms.size();
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
