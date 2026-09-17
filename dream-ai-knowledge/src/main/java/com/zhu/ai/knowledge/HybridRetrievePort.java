package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import com.zhu.ai.kernel.knowledge.RetrievePort;
import java.util.List;

/**
 * 两路线性混合的便捷 {@link RetrievePort}：内部委托 {@link LinearWeightedFusion}。
 * <p>
 * 新装配请优先用 {@link MultiSourceRecallStage} + {@link StagedRetrievePort}（可多路、可换融合策略）。
 * 本类保留给单测与「只要一个 Port、不要整段管线」的场景。
 */
public final class HybridRetrievePort implements RetrievePort {

    private final RetrievePort lexical;

    private final RetrievePort dense;

    private final double vectorWeight;

    private final ScoreFusion fusion = new LinearWeightedFusion();

    /**
     * @param lexical      关键词检索；{@code null} 视为该路恒空
     * @param dense        向量检索；{@code null} 视为该路恒空
     * @param vectorWeight α，必须在 {@code [0, 1]}，否则构造失败
     */
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
        return fusion.fuse(
                List.of(
                        new ScoreFusion.ChannelHits("lexical", kw, 1.0 - vectorWeight),
                        new ScoreFusion.ChannelHits("dense", vec, vectorWeight)),
                topK);
    }
}
