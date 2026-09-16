package com.zhu.ai.memory;

import com.zhu.ai.kernel.memory.MemoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 超软上限则调用 {@link MemorySummarizer} 写回。不实现 {@link MemoryPort}，避免装饰器套娃难排查。
 * 由 {@link MemorySummarizeListener} 在异步事件里调用。
 */
public final class MemorySummarizeService {

    private static final Logger log = LoggerFactory.getLogger(MemorySummarizeService.class);

    public static final int DEFAULT_SOFT_MAX_CHARS = 3000;

    public static final int DEFAULT_TARGET_CHARS = 1200;

    private final MemoryPort memory;
    private final MemorySummarizer summarizer;
    private final int softMaxChars;
    private final int targetChars;

    public MemorySummarizeService(MemoryPort memory, MemorySummarizer summarizer) {
        this(memory, summarizer, DEFAULT_SOFT_MAX_CHARS, DEFAULT_TARGET_CHARS);
    }

    public MemorySummarizeService(
            MemoryPort memory, MemorySummarizer summarizer, int softMaxChars, int targetChars) {
        this.memory = memory;
        this.summarizer = summarizer;
        this.softMaxChars = softMaxChars > 0 ? softMaxChars : DEFAULT_SOFT_MAX_CHARS;
        this.targetChars = targetChars > 0 ? targetChars : DEFAULT_TARGET_CHARS;
    }

    /**
     * 读当前笔记；未超软上限则直接返回。摘要失败保留原笔记（含底层硬截断结果）。
     */
    public void summarizeIfNeeded(String memoryKey) {
        if (memoryKey == null || memoryKey.isBlank()) {
            return;
        }
        String notes = memory.recall(memoryKey);
        if (notes.length() <= softMaxChars) {
            return;
        }
        try {
            String summary = summarizer.summarize(notes, targetChars);
            if (summary == null || summary.isBlank()) {
                log.warn("memory summarize skipped (blank) key={} chars={}", memoryKey, notes.length());
                return;
            }
            String written = "Summary:\n" + summary.trim() + "\n";
            memory.replaceNotes(memoryKey, written);
            log.info(
                    "memory summarized key={} beforeChars={} afterChars={}",
                    memoryKey,
                    notes.length(),
                    memory.recall(memoryKey).length());
        } catch (RuntimeException ex) {
            log.warn(
                    "memory summarize failed key={} chars={} error={}",
                    memoryKey,
                    notes.length(),
                    ex.getMessage());
        }
    }
}
