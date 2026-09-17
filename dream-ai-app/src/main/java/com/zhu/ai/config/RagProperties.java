package com.zhu.ai.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RAG 召回参数：{@code top-k}、门槛、融合策略与阶段列表。
 * 调低 {@code min-score} 召回升、噪声升；调高更干净但易漏。
 */
public class RagProperties {

    /** 最多返回几条命中。 */
    private int topK = 3;

    /** [0,1]：pgvector similarityThreshold；内存实现用命中词数/查询词数。 */
    private double minScore = 0.0;

    /** 非空则只保留该 docType；空 = 不过滤。 */
    private String docType = "";

    /** 混合检索中向量分权重；无向量库时忽略；仅 linear 融合使用。 */
    private double hybridAlpha = 0.6;

    /**
     * 未配置 {@link #stages} 时：true → 末段 rerank；false → truncate。
     * 若显式配置了 stages，本字段不再决定管线形状。
     */
    private boolean rerank = true;

    /** 融合：{@code linear}（默认）或 {@code rrf}。 */
    private String fusion = "linear";

    /**
     * 阶段名列表：{@code recall} / {@code filter} / {@code rerank} / {@code truncate}。
     * 空 = 按 {@link #rerank} 生成默认列表。
     */
    private List<String> stages = new ArrayList<>();

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public double getHybridAlpha() {
        return hybridAlpha;
    }

    public void setHybridAlpha(double hybridAlpha) {
        this.hybridAlpha = hybridAlpha;
    }

    public boolean isRerank() {
        return rerank;
    }

    public void setRerank(boolean rerank) {
        this.rerank = rerank;
    }

    public String getFusion() {
        return fusion;
    }

    public void setFusion(String fusion) {
        this.fusion = fusion;
    }

    public List<String> getStages() {
        return stages;
    }

    public void setStages(List<String> stages) {
        this.stages = stages == null ? new ArrayList<>() : stages;
    }

    public int normalizedTopK() {
        return topK <= 0 ? 3 : topK;
    }

    public double normalizedMinScore() {
        if (minScore < 0.0) {
            return 0.0;
        }
        if (minScore > 1.0) {
            return 1.0;
        }
        return minScore;
    }

    public String normalizedDocType() {
        return docType == null ? "" : docType.trim();
    }

    public double normalizedHybridAlpha() {
        if (hybridAlpha < 0.0) {
            return 0.0;
        }
        if (hybridAlpha > 1.0) {
            return 1.0;
        }
        return hybridAlpha;
    }

    public String normalizedFusion() {
        String key = fusion == null ? "" : fusion.trim().toLowerCase(Locale.ROOT);
        return key.isEmpty() ? "linear" : key;
    }

    /**
     * 解析阶段列表；未配置时回落到 {@code recall → filter → (rerank|truncate)}。
     */
    public List<String> normalizedStages() {
        if (stages == null || stages.isEmpty()) {
            return List.copyOf(com.zhu.ai.knowledge.RetrieveStages.defaultStageNames(rerank));
        }
        List<String> out = new ArrayList<>(stages.size());
        for (String raw : stages) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            out.add(raw.trim().toLowerCase(Locale.ROOT));
        }
        if (out.isEmpty()) {
            return List.copyOf(com.zhu.ai.knowledge.RetrieveStages.defaultStageNames(rerank));
        }
        return List.copyOf(out);
    }
}
