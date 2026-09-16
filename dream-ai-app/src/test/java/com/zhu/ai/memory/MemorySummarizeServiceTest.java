package com.zhu.ai.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MemorySummarizeServiceTest {

    @Test
    void underSoftMaxDoesNotSummarize() {
        AtomicInteger calls = new AtomicInteger();
        InMemoryMemoryPort memory = new InMemoryMemoryPort();
        MemorySummarizeService service =
                new MemorySummarizeService(memory, (notes, target) -> {
                    calls.incrementAndGet();
                    return "should-not-run";
                }, 500, 200);

        memory.rememberRound("s1", "我叫小明", "记下了");
        service.summarizeIfNeeded("s1");
        assertEquals(0, calls.get());
        assertTrue(memory.recall("s1").contains("小明"));
        assertFalse(memory.recall("s1").startsWith("Summary:"));
    }

    @Test
    void overSoftMaxReplacesWithSummaryAndKeepsFact() {
        AtomicInteger calls = new AtomicInteger();
        InMemoryMemoryPort memory = new InMemoryMemoryPort();
        MemorySummarizeService service =
                new MemorySummarizeService(memory, (notes, target) -> {
                    calls.incrementAndGet();
                    assertTrue(notes.contains("小明"));
                    assertTrue(target > 0);
                    return "用户名叫小明；偏好简洁回答。";
                }, 80, 40);

        memory.replaceNotes("s1", "U: 我叫小明\nA: 好的\n" + "pad ".repeat(40));
        assertTrue(memory.recall("s1").length() > 80);
        memory.rememberRound("s1", "今天天气如何", "还行");
        service.summarizeIfNeeded("s1");

        assertEquals(1, calls.get());
        String notes = memory.recall("s1");
        assertTrue(notes.startsWith("Summary:"));
        assertTrue(notes.contains("小明"));
        assertTrue(notes.length() < 200);
    }

    @Test
    void summarizerFailureKeepsHardTrimmedStore() {
        InMemoryMemoryPort memory = new InMemoryMemoryPort();
        MemorySummarizeService service =
                new MemorySummarizeService(memory, (notes, target) -> {
                    throw new IllegalStateException("boom");
                }, 50, 20);

        memory.rememberRound("s1", "fact-keep", "ok");
        memory.rememberRound("s1", "y".repeat(80), "z");
        service.summarizeIfNeeded("s1");
        String notes = memory.recall("s1");
        assertFalse(notes.startsWith("Summary:"));
        assertTrue(notes.contains("A: z\n") || notes.endsWith("A: z\n"));
    }

    @Test
    void blankSummarySkipsReplace() {
        InMemoryMemoryPort memory = new InMemoryMemoryPort();
        MemorySummarizeService service =
                new MemorySummarizeService(memory, (notes, target) -> "  ", 40, 20);
        memory.rememberRound("s1", "a".repeat(50), "b");
        service.summarizeIfNeeded("s1");
        assertFalse(memory.recall("s1").startsWith("Summary:"));
        assertTrue(memory.recall("s1").contains("A: b"));
    }

    @Test
    void listenerDelegatesToService() {
        AtomicInteger calls = new AtomicInteger();
        InMemoryMemoryPort memory = new InMemoryMemoryPort();
        memory.replaceNotes("s1", "x".repeat(100));
        MemorySummarizeService service =
                new MemorySummarizeService(memory, (notes, target) -> {
                    calls.incrementAndGet();
                    return "ok";
                }, 50, 20);
        MemorySummarizeListener listener = new MemorySummarizeListener(service);
        listener.onRemembered(new MemoryRoundRememberedEvent("s1"));
        assertEquals(1, calls.get());
        assertTrue(memory.recall("s1").startsWith("Summary:"));
    }
}
