package com.zhu.ai.kernel.knowledge;

import java.util.List;

/**
 * 检索端口。由 {@code dream-ai-knowledge} 实现；agents 只依赖本接口。
 * 由 {@link com.zhu.ai.kernel.runtime.AgentGateway} 在调用 Handler 前检索，Agent 不注入本端口。
 */
public interface RetrievePort {

    /**
     * 按 query 取最相关片段，最多 {@code topK} 条。无命中返回空列表。
     */
    List<String> retrieve(String query, int topK);
}
