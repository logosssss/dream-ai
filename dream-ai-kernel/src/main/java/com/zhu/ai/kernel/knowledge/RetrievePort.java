package com.zhu.ai.kernel.knowledge;

import java.util.List;

/**
 * 检索能力契约（hexagonal Port，不是 HTTP/TCP 端口）。
 * 由 {@code dream-ai-knowledge} / app 的 pgvector 适配实现；agents 只依赖本接口。
 * 由 {@link com.zhu.ai.kernel.runtime.AgentGateway} 在调用 Handler 前检索，Agent 不注入本契约、也不碰 VectorStore。
 */
public interface RetrievePort {

    /**
     * 按 query 取最相关片段，最多 {@code topK} 条。无命中或低于实现侧门槛时返回空列表。
     * 返回结构化 {@link RetrieveHit}；拼进 prompt 的编号引用由 Gateway {@link RetrieveCitations} 完成。
     */
    List<RetrieveHit> retrieve(String query, int topK);
}
