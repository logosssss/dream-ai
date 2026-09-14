package com.zhu.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LocalMethodToolsTest {

    private final LocalMethodTools tools = new LocalMethodTools();

    @Test
    void countsAsciiAndDetectsNoCjk() {
        LocalMethodTools.TextStats stats = tools.textStats("Hello");
        assertEquals(5, stats.charCount());
        assertEquals(5, stats.codePointCount());
        assertFalse(stats.hasCjk());
        assertEquals("Hello", stats.preview());
    }

    @Test
    void detectsCjkAndTruncatesPreview() {
        String longText = "中".repeat(50);
        LocalMethodTools.TextStats stats = tools.textStats(longText);
        assertEquals(50, stats.charCount());
        assertTrue(stats.hasCjk());
        assertTrue(stats.preview().endsWith("…"));
        assertEquals(41, stats.preview().length());
    }

    @Test
    void nullTreatedAsEmpty() {
        LocalMethodTools.TextStats stats = tools.textStats(null);
        assertEquals(0, stats.charCount());
        assertFalse(stats.hasCjk());
    }
}
