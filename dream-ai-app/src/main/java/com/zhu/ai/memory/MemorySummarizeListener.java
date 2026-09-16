package com.zhu.ai.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 事件驱动摘要：不挡 Gateway 主路径。仅在存在 {@link MemorySummarizer} Bean 时装配。
 */
@Component
@ConditionalOnBean(MemorySummarizer.class)
public class MemorySummarizeListener {

    private static final Logger log = LoggerFactory.getLogger(MemorySummarizeListener.class);

    private final MemorySummarizeService service;

    public MemorySummarizeListener(MemorySummarizeService service) {
        this.service = service;
    }

    @Async("memorySummarizeExecutor")
    @EventListener
    public void onRemembered(MemoryRoundRememberedEvent event) {
        if (event == null || event.memoryKey() == null || event.memoryKey().isBlank()) {
            return;
        }
        log.debug("memory summarize async key={}", event.memoryKey());
        service.summarizeIfNeeded(event.memoryKey());
    }
}
