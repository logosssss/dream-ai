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
 * 进程内关键词检索：无向量库、无独立 8096。入库后按词项重叠打分。
 * {@code minScore} 是重叠率门槛（命中词数 / 查询词数），与 pgvector 的 similarityThreshold 同一量纲 [0,1]。
 */
public final class InMemoryKeywordIndex implements RetrievePort {

    static final int CHUNK_SIZE = 280;

    static final String SOURCE = "in-memory";

    private final CopyOnWriteArrayList<Chunk> chunks = new CopyOnWriteArrayList<>();

    private final AtomicInteger seq = new AtomicInteger();

    private final double minScore;

    public InMemoryKeywordIndex() {
        this(0.0);
    }

    public InMemoryKeywordIndex(double minScore) {
        if (minScore < 0.0 || minScore > 1.0) {
            throw new IllegalArgumentException("minScore must be in [0, 1]");
        }
        this.minScore = minScore;
    }

    public void ingest(String text) {
        ingest(text, SOURCE);
    }

    public void ingest(String text, String source) {
        ingest(text, source, "");
    }

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

    private static boolean hasHan(String text) {
        return text.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    private record Chunk(String id, String text, String source, String docType) {}
}
