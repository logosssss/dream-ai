package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 轻量二次排序（rerank）：在「已经召回的候选」上按查询再打一次可解释分，再截断到展示用 {@code topK}。
 * <p>
 * <b>和召回的分工</b>：上游（关键词 / 向量 / {@link HybridRetrievePort}）负责尽量把相关片段捞进来，
 * 召回可以偏宽；本类负责「展示要准」——压低又长又空的高分噪声，抬高与 query 词重叠更高的短片段。
 * 典型接法见 {@link RetrieveStages} / {@link RerankStage}：召回加宽 → 过滤 → 本类精排截断。
 * <p>
 * <b>融合分（写死权重，故意简单）</b>：
 * <pre>
 *   fused = 0.50 · clamp01(原 score)
 *         + 0.35 · 关键词重叠率
 *         + 0.15 · 简洁度
 * </pre>
 * <ul>
 *   <li>原分：向量余弦 / 混合分等，先夹到 {@code [0,1]}，避免上游分制失控</li>
 *   <li>关键词重叠：命中 query 词项数 / 词项总数，词项规则与 {@link InMemoryKeywordIndex#terms} 一致</li>
 *   <li>简洁度：{@code 1 / (1 + len / SOFT_LEN)}，越短越高，抑制「超长灌水仍高分」</li>
 * </ul>
 * 不调 LLM、不引第三方 cross-encoder：沙盘可测、可讲清；真要上模型裁判再换实现，Port 边界不用动。
 * <p>
 * <b>输出</b>：返回新的 {@link RetrieveHit} 列表，{@code score} 换成 fused（便于 observe / 引用排序对照）；
 * id / text / source / docType 原样保留。无状态工具类，禁止实例化。
 */
public final class RetrieveRerank {

    /**
     * 简洁度软阈值（字符）。约等于内存索引默认 chunk 长度：
     * 短于该长度时 brevity 衰减慢，明显长于时惩罚变陡。
     */
    static final int SOFT_LEN = 280;

    private RetrieveRerank() {}

    /**
     * 对候选重排并截断。
     *
     * @param query 用户问题；用于算关键词重叠（与入库检索同一套分词）
     * @param hits  上游召回列表；可为任意顺序
     * @param topK  最终保留条数；{@code ≤ 0} 或 hits 空 → 空列表
     * @return 按 fused 降序的新列表，长度 {@code ≤ topK}；元素为新 {@link RetrieveHit}（score=fused）
     */
    public static List<RetrieveHit> rerank(String query, List<RetrieveHit> hits, int topK) {
        if (hits == null || hits.isEmpty() || topK <= 0) {
            return List.of();
        }
        Set<String> terms = InMemoryKeywordIndex.terms(query == null ? "" : query);
        record Row(RetrieveHit hit, double fused) {}
        List<Row> rows = new ArrayList<>(hits.size());
        for (RetrieveHit hit : hits) {
            double keyword = keywordOverlap(hit.text(), terms);
            // len=0 → 1.0；len=SOFT_LEN → 0.5；len→∞ → 0
            double brevity = 1.0 / (1.0 + hit.text().length() / (double) SOFT_LEN);
            double fused = 0.5 * clamp01(hit.score()) + 0.35 * keyword + 0.15 * brevity;
            rows.add(new Row(hit, fused));
        }
        rows.sort(Comparator.comparingDouble(Row::fused).reversed());
        int limit = Math.min(topK, rows.size());
        List<RetrieveHit> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            RetrieveHit hit = rows.get(i).hit();
            out.add(new RetrieveHit(hit.id(), hit.text(), rows.get(i).fused(), hit.source(), hit.docType()));
        }
        return List.copyOf(out);
    }

    /**
     * 片段正文对 query 词项的覆盖率：{@code 命中词数 / 词项总数}，落在 {@code [0, 1]}。
     * 词项为空（query 太短）或正文空 → 0，不抬分也不抛错。
     */
    static double keywordOverlap(String text, Set<String> terms) {
        if (terms.isEmpty() || text == null || text.isBlank()) {
            return 0.0;
        }
        String hay = text.toLowerCase(Locale.ROOT);
        int matched = 0;
        for (String term : terms) {
            if (hay.contains(term)) {
                matched++;
            }
        }
        return matched / (double) terms.size();
    }

    /** 把上游分数夹到 {@code [0, 1]}，防止负分或 &gt;1 的原始分扭曲加权。 */
    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
