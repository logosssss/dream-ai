package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import java.util.ArrayList;
import java.util.List;

/**
 * metadata 过滤。{@code docType} 为空则不过滤。
 */
public final class RetrieveFilters {

    private RetrieveFilters() {}

    public static List<RetrieveHit> byDocType(List<RetrieveHit> hits, String docType) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        if (docType == null || docType.isBlank()) {
            return List.copyOf(hits);
        }
        String want = docType.trim();
        List<RetrieveHit> out = new ArrayList<>();
        for (RetrieveHit hit : hits) {
            if (want.equalsIgnoreCase(hit.docType())) {
                out.add(hit);
            }
        }
        return List.copyOf(out);
    }
}
