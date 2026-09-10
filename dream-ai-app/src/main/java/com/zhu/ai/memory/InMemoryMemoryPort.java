package com.zhu.ai.memory;

import com.zhu.ai.kernel.memory.MemoryPort;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内长期笔记。空 key 不读写；超出 {@link #MAX_CHARS} 保留尾部。
 * 单测和 Redis 未装配时使用。
 */
public final class InMemoryMemoryPort implements MemoryPort {

    public static final int MAX_CHARS = 4000;

    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    @Override
    public String recall(String memoryKey) {
        if (blank(memoryKey)) {
            return "";
        }
        String notes = store.get(memoryKey);
        return notes == null ? "" : notes;
    }

    @Override
    public void rememberRound(String memoryKey, String userInput, String assistantOutput) {
        if (blank(memoryKey)) {
            return;
        }
        store.compute(memoryKey, (key, old) -> trim((old == null ? "" : old) + line(userInput, assistantOutput)));
    }

    public static String line(String userInput, String assistantOutput) {
        return "U: " + nullToEmpty(userInput) + "\nA: " + nullToEmpty(assistantOutput) + "\n";
    }

    public static String trim(String notes) {
        if (notes.length() <= MAX_CHARS) {
            return notes;
        }
        return notes.substring(notes.length() - MAX_CHARS);
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private static boolean blank(String memoryKey) {
        return memoryKey == null || memoryKey.isBlank();
    }
}
