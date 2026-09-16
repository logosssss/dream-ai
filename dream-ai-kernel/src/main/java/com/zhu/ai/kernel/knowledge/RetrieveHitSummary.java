package com.zhu.ai.kernel.knowledge;

/**
 * 检索命中摘要（不含正文），给观测 / HTTP / SSE {@code event:retrieve}。
 * 与 prompt 里 {@code [1]} {@code [2]} 按列表顺序对照。
 */
public record RetrieveHitSummary(String id, double score, String source) {

    public RetrieveHitSummary {
        id = id == null ? "" : id;
        source = source == null ? "" : source;
    }
}
