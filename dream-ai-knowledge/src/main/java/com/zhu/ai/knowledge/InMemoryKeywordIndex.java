package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrievePort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 进程内关键词检索：无向量库、无独立 8096。入库后按词项重叠打分。
 */
public final class InMemoryKeywordIndex implements RetrievePort {

    static final int CHUNK_SIZE = 280;

    private final CopyOnWriteArrayList<String> chunks = new CopyOnWriteArrayList<>();

    public void ingest(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        chunks.addAll(chunk(text));
    }

    @Override
    public List<String> retrieve(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0 || chunks.isEmpty()) {
            return List.of();
        }
        Set<String> terms = terms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        record Scored(String chunk, int score) {}
        List<Scored> scored = new ArrayList<>();
        for (String chunk : chunks) {
            int score = 0;
            String hay = chunk.toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (hay.contains(term)) {
                    score++;
                }
            }
            if (score > 0) {
                scored.add(new Scored(chunk, score));
            }
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed());
        int limit = Math.min(topK, scored.size());
        List<String> hits = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            hits.add(scored.get(i).chunk());
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

    static Set<String> terms(String query) {
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
}
