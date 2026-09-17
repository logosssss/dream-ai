package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 线性加权融合：{@code score = Σ (w_i · s_i)}，按正文去重。
 * <p>
 * 行为对齐原 {@link HybridRetrievePort}：
 * <ul>
 *   <li>只有一路有命中 → 原分截断，不乘权（避免单路被 α 压扁）</li>
 *   <li>多路有命中 → 按正文合并加权分，元数据偏向「权重大」的那路，权重相同则后写覆盖</li>
 * </ul>
 * 权重应大致归一；调用方负责（如 hybrid α 与 1−α）。排名融合见 {@link ReciprocalRankFusion}。
 */
public final class LinearWeightedFusion implements ScoreFusion {

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
            return cap(nonEmpty.get(0).hits(), limit);
        }
        Map<String, Acc> byText = new LinkedHashMap<>();
        for (ChannelHits ch : nonEmpty) {
            double w = ch.weight();
            for (RetrieveHit hit : ch.hits()) {
                Acc acc = byText.get(hit.text());
                if (acc == null) {
                    byText.put(hit.text(), new Acc(hit, hit.score() * w, w));
                } else {
                    acc.add(hit, hit.score() * w, w);
                }
            }
        }
        return byText.values().stream()
                .map(Acc::toHit)
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(limit)
                .toList();
    }

    private static List<RetrieveHit> cap(List<RetrieveHit> hits, int limit) {
        if (hits.size() <= limit) {
            return hits;
        }
        return List.copyOf(hits.subList(0, limit));
    }

    /** 同文累加分；元数据取「贡献权重最大」的那条命中（平局保留后写）。 */
    private static final class Acc {
        private RetrieveHit preferred;
        private double preferredWeight;
        private double score;

        Acc(RetrieveHit hit, double weightedScore, double weight) {
            this.preferred = hit;
            this.preferredWeight = weight;
            this.score = weightedScore;
        }

        void add(RetrieveHit hit, double weightedScore, double weight) {
            score += weightedScore;
            if (weight >= preferredWeight) {
                preferred = hit;
                preferredWeight = weight;
            }
        }

        RetrieveHit toHit() {
            return new RetrieveHit(
                    preferred.id(), preferred.text(), score, preferred.source(), preferred.docType());
        }
    }
}
