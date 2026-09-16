package com.zhu.ai.config;

/**
 * RAG 召回参数：{@code top-k} 与相似度 / 关键词重叠率门槛。
 * 调低 {@code min-score} 召回升、噪声升；调高更干净但易漏。
 */
public class RagProperties {

    /** 最多返回几条命中。 */
    private int topK = 3;

    /** [0,1]：pgvector similarityThreshold；内存实现用命中词数/查询词数。 */
    private double minScore = 0.0;

    /** 非空则只保留该 docType；空 = 不过滤。 */
    private String docType = "";

    /** 混合检索中向量分权重；无向量库时忽略。 */
    private double hybridAlpha = 0.6;

    /** 召回加宽后按关键词+长度再排。 */
    private boolean rerank = true;

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
}
