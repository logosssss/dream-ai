package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion（RRF）：按各路排名融合，弱化绝对分制差异。
 * <pre>
 *   score(d) = Σ_i  w_i / (k + rank_i(d))
 * </pre>
 * {@code rank} 从 1 起；默认 {@code k=60}。权重默认用通道 weight，全 0 时当 1。
 * 元数据取「贡献分最高」的那路命中。
 */
public final class ReciprocalRankFusion implements ScoreFusion {

    public static final int DEFAULT_K = 60;

    private final int k;

    public ReciprocalRankFusion() {
        this(DEFAULT_K);
    }

    public ReciprocalRankFusion(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1");
        }
        this.k = k;
    }

    @Override
    public List<RetrieveHit> fuse(List<ChannelHits> channels, int limit) {
        if (limit <= 0 || channels == null || channels.isEmpty()) {
            return List.of();
        }
        List<ChannelHits> nonEmpty = new ArrayList<>();
        for (ChannelHits ch : channels) {
            if (ch != null && ch.hits() != null && !ch.hits().isEmpty()) {
                nonEmpty.add(ch);
            }
        }
        if (nonEmpty.isEmpty()) {
            return List.of();
        }
        if (nonEmpty.size() == 1) {
            List<RetrieveHit> only = nonEmpty.get(0).hits();
            if (only.size() <= limit) {
                return only;
            }
            return List.copyOf(only.subList(0, limit));
        }
        Map<String, Acc> byText = new LinkedHashMap<>();
        for (ChannelHits ch : nonEmpty) {
            double w = ch.weight() <= 0.0 ? 1.0 : ch.weight();
            List<RetrieveHit> hits = ch.hits();
            for (int i = 0; i < hits.size(); i++) {
                RetrieveHit hit = hits.get(i);
                double contrib = w / (k + i + 1);
                Acc acc = byText.get(hit.text());
                if (acc == null) {
                    byText.put(hit.text(), new Acc(hit, contrib));
                } else {
                    acc.add(hit, contrib);
                }
            }
        }
        return byText.values().stream()
                .map(Acc::toHit)
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(limit)
                .toList();
    }

    private static final class Acc {
        private RetrieveHit preferred;
        private double preferredContrib;
        private double score;

        Acc(RetrieveHit hit, double contrib) {
            this.preferred = hit;
            this.preferredContrib = contrib;
            this.score = contrib;
        }

        void add(RetrieveHit hit, double contrib) {
            score += contrib;
            if (contrib >= preferredContrib) {
                preferred = hit;
                preferredContrib = contrib;
            }
        }

        RetrieveHit toHit() {
            return new RetrieveHit(
                    preferred.id(), preferred.text(), score, preferred.source(), preferred.docType());
        }
    }
}
