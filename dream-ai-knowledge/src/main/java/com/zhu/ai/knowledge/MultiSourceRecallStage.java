package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import com.zhu.ai.kernel.knowledge.RetrievePort;
import java.util.ArrayList;
import java.util.List;

/**
 * 召回阶段：多路 {@link RetrievePort} 各取加宽后的 topK，再经 {@link ScoreFusion} 合并。
 * <p>
 * 加宽因子默认 3（与旧 {@code RetrievePipeline} 一致）：先多捞一点，交给后面的过滤 / 精排。
 * 一路源时融合退化为截断；两路以上才真正 merge。输入列表忽略（应为管线首段）。
 */
public final class MultiSourceRecallStage implements RetrieveStage {

    private final List<WeightedSource> sources;

    private final ScoreFusion fusion;

    private final int widenFactor;

    public MultiSourceRecallStage(List<WeightedSource> sources, ScoreFusion fusion) {
        this(sources, fusion, 3);
    }

    public MultiSourceRecallStage(List<WeightedSource> sources, ScoreFusion fusion, int widenFactor) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("sources required");
        }
        this.sources = List.copyOf(sources);
        this.fusion = fusion == null ? new LinearWeightedFusion() : fusion;
        this.widenFactor = Math.max(1, widenFactor);
    }

    @Override
    public String name() {
        return "recall";
    }

    @Override
    public List<RetrieveHit> apply(RetrieveContext ctx, List<RetrieveHit> input) {
        int recallK = Math.max(ctx.topK() * widenFactor, ctx.topK());
        List<ScoreFusion.ChannelHits> channels = new ArrayList<>(sources.size());
        for (WeightedSource source : sources) {
            List<RetrieveHit> hits = source.port() == null
                    ? List.of()
                    : source.port().retrieve(ctx.query(), recallK);
            channels.add(new ScoreFusion.ChannelHits(source.name(), hits, source.weight()));
        }
        return fusion.fuse(channels, recallK);
    }

    /**
     * 一路加权召回源。
     *
     * @param name   通道名（日志 / 埋点）
     * @param port   实际检索实现
     * @param weight 融合权重
     */
    public record WeightedSource(String name, RetrievePort port, double weight) {
        public WeightedSource {
            name = name == null || name.isBlank() ? "source" : name;
            if (weight < 0.0) {
                throw new IllegalArgumentException("weight must be >= 0");
            }
        }
    }
}
