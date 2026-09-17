package com.zhu.ai.knowledge;

/**
 * 按名字创建 {@link ScoreFusion}：{@code linear} / {@code rrf}（大小写不敏感）。
 */
public final class ScoreFusions {

    private ScoreFusions() {}

    public static ScoreFusion create(String name) {
        String key = name == null ? "" : name.trim().toLowerCase();
        if (key.isEmpty() || "linear".equals(key) || "weighted".equals(key)) {
            return new LinearWeightedFusion();
        }
        if ("rrf".equals(key) || "reciprocal".equals(key)) {
            return new ReciprocalRankFusion();
        }
        throw new IllegalArgumentException("unknown fusion: " + name + " (use linear|rrf)");
    }
}
