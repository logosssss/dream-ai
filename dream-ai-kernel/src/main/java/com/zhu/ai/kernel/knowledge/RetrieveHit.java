package com.zhu.ai.kernel.knowledge;

/**
 * 一条检索命中。id/score/source 给观测与排障；text 由 Gateway 编成带编号的参考资料。
 * {@code docType} 供 metadata 过滤（如 intro / api）；空表示未标注。
 * Agent 只消费格式化后的字符串，不接触 VectorStore。
 */
public record RetrieveHit(String id, String text, double score, String source, String docType) {

    public RetrieveHit {
        id = id == null ? "" : id;
        text = text == null ? "" : text;
        source = source == null ? "" : source;
        docType = docType == null ? "" : docType;
    }

    public RetrieveHit(String id, String text, double score, String source) {
        this(id, text, score, source, "");
    }

    public RetrieveHitSummary summary() {
        return new RetrieveHitSummary(id, score, source);
    }
}
