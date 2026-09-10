package com.zhu.ai.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InMemoryMemoryPortTest {

    @Test
    void blankKeyIsNoop() {
        InMemoryMemoryPort port = new InMemoryMemoryPort();
        port.rememberRound("  ", "u", "a");
        assertEquals("", port.recall(null));
        assertEquals("", port.recall(""));
    }

    @Test
    void rememberAndRecall() {
        InMemoryMemoryPort port = new InMemoryMemoryPort();
        port.rememberRound("s1", "我叫小明", "记下了");
        String notes = port.recall("s1");
        assertTrue(notes.contains("我叫小明"));
        assertTrue(notes.contains("记下了"));
        assertEquals("", port.recall("s2"));
    }

    @Test
    void trimsOldestChars() {
        InMemoryMemoryPort port = new InMemoryMemoryPort();
        String fat = "x".repeat(InMemoryMemoryPort.MAX_CHARS);
        port.rememberRound("s1", fat, "a");
        assertEquals(InMemoryMemoryPort.MAX_CHARS, port.recall("s1").length());
        assertTrue(port.recall("s1").endsWith("A: a\n") || port.recall("s1").contains("A: a"));
    }
}
