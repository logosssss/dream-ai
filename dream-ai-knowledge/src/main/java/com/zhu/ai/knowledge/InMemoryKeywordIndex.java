package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import com.zhu.ai.kernel.knowledge.RetrievePort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进程内关键词（稀疏）检索：实现 {@link RetrievePort}，不依赖向量库、也不拆独立 8096。
 * <p>
 * <b>在栈里的位置</b>：单测 / 无 Postgres 时作为默认实现；有 pgvector 时可当
 * {@link HybridRetrievePort} 的 lexical 侧。Agent 仍只看见 Port，本类由 app
 * {@code PortsConfig} 装配。词项规则还被 {@link RetrieveRerank} 复用，保证「召回」与「二次排序」同尺。
 * <p>
 * <b>打分</b>：对每个 chunk，{@code score = 命中词项数 / 查询词项总数}（重叠率），落在 {@code (0, 1]}。
 * {@code minScore} 是门槛，语义对齐 pgvector 的 {@code similarityThreshold}（同为 {@code [0,1]}）：
 * 低于门槛的片段直接丢掉，减少闲聊噪声进 prompt。
 * <p>
 * <b>入库</b>：{@link #ingest} 先按空行分段，超长再按 {@link #CHUNK_SIZE} 切；每片分配稳定 id
 * {@code kw-N}，并带 source / docType，供观测引用与 {@link RetrieveFilters} 过滤。
 * 存储用 {@link CopyOnWriteArrayList}：启动 ingest 与请求检索可并发，读多写少。
 * <p>
 * <b>刻意不做</b>：倒排索引、BM25、持久化——沙盘优先可讲清「稀疏检索长什么样」；要语义召回走向量侧。
 */
public final class InMemoryKeywordIndex implements RetrievePort {

    /**
     * 单片最大字符数。与 {@link RetrieveRerank#SOFT_LEN} 同量级，避免一片过大灌进 system，
     * 也让 rerank 的简洁度惩罚和切块粒度大致对齐。
     */
    static final int CHUNK_SIZE = 280;

    /** 未指定 source 时的默认来源标记，出现在 {@link RetrieveHit#source()}。 */
    static final String SOURCE = "in-memory";

    private final CopyOnWriteArrayList<Chunk> chunks = new CopyOnWriteArrayList<>();

    /** 生成 {@code kw-1}、{@code kw-2}…；进程内唯一即可，不要求跨重启稳定。 */
    private final AtomicInteger seq = new AtomicInteger();

    /**
     * 重叠率门槛。{@code 0.0} = 任一词命中即保留；{@code 0.9} 等偏严，适合压噪声。
     * 构造时必须落在 {@code [0, 1]}。
     */
    private final double minScore;

    /** 等价于 {@code minScore = 0}：有重叠就召回。 */
    public InMemoryKeywordIndex() {
        this(0.0);
    }

    /**
     * @param minScore 重叠率门槛，{@code [0, 1]}；越界则构造失败
     */
    public InMemoryKeywordIndex(double minScore) {
        if (minScore < 0.0 || minScore > 1.0) {
            throw new IllegalArgumentException("minScore must be in [0, 1]");
        }
        this.minScore = minScore;
    }

    /** 入库；source 用 {@link #SOURCE}，docType 空。 */
    public void ingest(String text) {
        ingest(text, SOURCE);
    }

    /** 入库并标记来源（如文件名）；docType 空。 */
    public void ingest(String text, String source) {
        ingest(text, source, "");
    }

    /**
     * 切块写入内存索引。
     *
     * @param text    原文；空则忽略
     * @param source  来源；空白则回落 {@link #SOURCE}
     * @param docType 文档类型（如 intro）；供后续 metadata 过滤，可空
     */
    public void ingest(String text, String source, String docType) {
        if (text == null || text.isBlank()) {
            return;
        }
        String src = source == null || source.isBlank() ? SOURCE : source;
        String type = docType == null ? "" : docType.trim();
        for (String part : chunk(text)) {
            chunks.add(new Chunk("kw-" + seq.incrementAndGet(), part, src, type));
        }
    }

    /**
     * 按词项重叠率检索，降序取 {@code topK}。
     * <ol>
     *   <li>query / topK / 空库 → 空列表</li>
     *   <li>{@link #terms} 抽查询词；抽不出（过短）→ 空</li>
     *   <li>逐 chunk：统计 contains 命中数 → 重叠率；低于 {@code minScore} 丢弃</li>
     *   <li>排序截断，打成 {@link RetrieveHit}（score=重叠率）</li>
     * </ol>
     * 比较用 {@code ratio + 1e-9 >= minScore}，减轻双精度卡在门槛上的边界抖动。
     */
    @Override
    public List<RetrieveHit> retrieve(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0 || chunks.isEmpty()) {
            return List.of();
        }
        Set<String> terms = terms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        record Scored(Chunk chunk, double score) {}
        List<Scored> scored = new ArrayList<>();
        for (Chunk chunk : chunks) {
            int matched = 0;
            String hay = chunk.text().toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (hay.contains(term)) {
                    matched++;
                }
            }
            if (matched <= 0) {
                continue;
            }
            double ratio = matched / (double) terms.size();
            if (ratio + 1e-9 >= minScore) {
                scored.add(new Scored(chunk, ratio));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        int limit = Math.min(topK, scored.size());
        List<RetrieveHit> hits = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Scored row = scored.get(i);
            Chunk chunk = row.chunk();
            hits.add(new RetrieveHit(chunk.id(), chunk.text(), row.score(), chunk.source(), chunk.docType()));
        }
        return List.copyOf(hits);
    }

    /**
     * 切块策略：先按空行（连续换行）分成段落；单段超过 {@link #CHUNK_SIZE} 再按定长切开。
     * 启动 ingest intro、以及 Pg 入库前复用同一套切法，避免「内存能命中、向量侧切法不同」难对齐。
     */
    public static List<String> chunk(String text) {
        String normalized = text.replace("\r\n", "\n").trim();
        List<String> parts = new ArrayList<>();
        for (String para : normalized.split("\n{2,}")) {
            String p = para.trim();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() <= CHUNK_SIZE) {
                parts.add(p);
                continue;
            }
            for (int i = 0; i < p.length(); i += CHUNK_SIZE) {
                parts.add(p.substring(i, Math.min(i + CHUNK_SIZE, p.length())));
            }
        }
        return parts;
    }

    /**
     * 从查询抽词项（保序去重）。
     * <ul>
     *   <li>空白 / 标点切分，小写；长度 ≥ 2 的 token 入集</li>
     *   <li>若 token 含汉字：再滑窗加入相邻二元组（bigram），缓解中文无空格分词</li>
     * </ul>
     * 公开且静态：{@link RetrieveRerank} 等与本索引共用，避免两套分词漂移。
     */
    public static Set<String> terms(String query) {
        Set<String> out = new LinkedHashSet<>();
        for (String raw : query.toLowerCase(Locale.ROOT).split("[\\s\\p{Punct}]+")) {
            if (raw.length() >= 2) {
                out.add(raw);
            }
            if (raw.length() >= 2 && hasHan(raw)) {
                for (int i = 0; i < raw.length() - 1; i++) {
                    out.add(raw.substring(i, i + 2));
                }
            }
        }
        return out;
    }

    /** 是否含汉字（用于决定要不要做 bigram）。 */
    private static boolean hasHan(String text) {
        return text.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    /** 内存中的一片语料；对外经 {@link RetrieveHit} 暴露。 */
    private record Chunk(String id, String text, String source, String docType) {}
}
