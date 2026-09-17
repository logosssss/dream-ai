package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import java.util.List;

/**
 * 多路召回结果的融合策略。换 RRF / 其它算法时只换实现，不必改 {@link MultiSourceRecallStage}。
 */
public interface ScoreFusion {

    /**
     * @param channels 各路命中（含名称与权重）；可能有空路
     * @param limit    融合后最多保留条数
     */
    List<RetrieveHit> fuse(List<ChannelHits> channels, int limit);

    /**
     * 一路召回快照。
     *
     * @param name    通道名（lexical / dense / …）
     * @param hits    该路原始命中
     * @param weight  线性融合权重；RRF 实现可忽略
     */
    record ChannelHits(String name, List<RetrieveHit> hits, double weight) {
        public ChannelHits {
            name = name == null ? "" : name;
            hits = hits == null ? List.of() : List.copyOf(hits);
        }
    }
}
