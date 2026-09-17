package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import com.zhu.ai.kernel.knowledge.RetrievePort;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * 稠密检索适配器：用 Spring AI {@link VectorStore}（本仓为 PgVector）实现 {@link RetrievePort}。
 * <p>
 * <b>为什么放在 app 而不是 knowledge 模块</b>：真正依赖的是 Spring AI / JDBC / Embedding，
 * 这些属于厂商与基础设施；{@code dream-ai-knowledge} 只放无 Spring 的稀疏实现与管道
 *（{@link com.zhu.ai.knowledge.InMemoryKeywordIndex}、{@link com.zhu.ai.knowledge.HybridRetrievePort} 等）。
 * Agent / Gateway 仍然只注入 {@link RetrievePort}，绝不直接碰 {@link VectorStore}。
 * <p>
 * <b>在装配中的角色</b>：有 VectorStore Bean 时由 {@code PortsConfig} 创建本类；常与内存关键词组成
 * {@link com.zhu.ai.knowledge.HybridRetrievePort} 的 dense 侧。{@code minScore} 来自
 * {@code dream.rag.min-score}，写入 {@link SearchRequest#getSimilarityThreshold()}，
 * 与内存侧重叠率门槛同一配置键、同一 {@code [0,1]} 量纲，便于对照调参。
 * <p>
 * <b>字段映射</b>：{@link Document} → {@link RetrieveHit}：
 * id / text / score 直接取；{@code source}、{@code docType} 从 metadata 读（入库时写入）。
 * 空白正文跳过；score 为 null 时当 0。本类不做 rerank / docType 过滤——那是
 * {@link com.zhu.ai.knowledge.RetrieveStages} 管线的职责。
 */
public final class PgVectorRetrievePort implements RetrievePort {

    private final VectorStore vectorStore;

    /**
     * 相似度门槛，传给 {@code SearchRequest.similarityThreshold}。
     * {@code 0.0} = 几乎不过滤；越高越严。必须落在 {@code [0, 1]}。
     */
    private final double minScore;

    /** 等价于 {@code minScore = 0}：只按距离取 topK。 */
    public PgVectorRetrievePort(VectorStore vectorStore) {
        this(vectorStore, 0.0);
    }

    /**
     * @param vectorStore 非空的 Spring AI 向量库（通常 PgVectorStore）
     * @param minScore    相似度门槛 {@code [0, 1]}；越界则构造失败
     */
    public PgVectorRetrievePort(VectorStore vectorStore, double minScore) {
        if (minScore < 0.0 || minScore > 1.0) {
            throw new IllegalArgumentException("minScore must be in [0, 1]");
        }
        this.vectorStore = vectorStore;
        this.minScore = minScore;
    }

    /**
     * 向量相似度检索并映射为结构化命中。
     * <ol>
     *   <li>query 空白或 {@code topK ≤ 0} → 空列表，且<strong>不</strong>打库</li>
     *   <li>{@link VectorStore#similaritySearch}：query + topK + threshold</li>
     *   <li>逐条 Document 转 {@link RetrieveHit}；无正文则跳过</li>
     * </ol>
     * Embedding 与 SQL 细节封在 VectorStore 实现里；本适配器只做 Port 边界翻译。
     */
    @Override
    public List<RetrieveHit> retrieve(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(minScore)
                        .build());
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        List<RetrieveHit> hits = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            String text = doc.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            double score = doc.getScore() == null ? 0.0 : doc.getScore();
            Object source = doc.getMetadata() == null ? null : doc.getMetadata().get("source");
            Object docType = doc.getMetadata() == null ? null : doc.getMetadata().get("docType");
            hits.add(new RetrieveHit(
                    doc.getId() == null ? "" : doc.getId(),
                    text,
                    score,
                    source == null ? "" : String.valueOf(source),
                    docType == null ? "" : String.valueOf(docType)));
        }
        return List.copyOf(hits);
    }
}
