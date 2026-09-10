package com.zhu.ai.memory;

import com.zhu.ai.kernel.memory.MemoryPort;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 长期笔记。进程重启后仍在；短会话 {@code ConversationPort} 仍是内存窗口。
 */
public final class RedisMemoryPort implements MemoryPort {

    static final String KEY_PREFIX = "dream-ai:mem:";

    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;

    public RedisMemoryPort(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String recall(String memoryKey) {
        if (blank(memoryKey)) {
            return "";
        }
        String notes = redis.opsForValue().get(KEY_PREFIX + memoryKey);
        return notes == null ? "" : notes;
    }

    @Override
    public void rememberRound(String memoryKey, String userInput, String assistantOutput) {
        if (blank(memoryKey)) {
            return;
        }
        String key = KEY_PREFIX + memoryKey;
        String merged = InMemoryMemoryPort.trim(
                (recall(memoryKey)) + InMemoryMemoryPort.line(userInput, assistantOutput));
        redis.opsForValue().set(key, merged, TTL);
    }

    private static boolean blank(String memoryKey) {
        return memoryKey == null || memoryKey.isBlank();
    }
}
