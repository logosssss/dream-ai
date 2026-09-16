package com.zhu.ai.config;

import com.zhu.ai.memory.ChatModelMemorySummarizer;
import com.zhu.ai.memory.MemorySummarizeService;
import com.zhu.ai.memory.MemorySummarizer;
import com.zhu.ai.kernel.memory.MemoryPort;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Memory 摘要异步：事件监听走独立线程池，不阻塞 invoke。
 */
@Configuration
@EnableAsync
public class MemoryAsyncConfig {

    @Bean(name = "memorySummarizeExecutor")
    TaskExecutor memorySummarizeExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("mem-sum-");
        executor.setVirtualThreads(true);
        return executor;
    }

    @Bean
    @ConditionalOnBean(ChatModel.class)
    MemorySummarizer memorySummarizer(
            ChatModel chatModel,
            @Value("${spring.ai.dashscope.chat.options.multi-model:#{null}}") Boolean multiModel) {
        return new ChatModelMemorySummarizer(chatModel, multiModel);
    }

    @Bean
    @ConditionalOnBean(MemorySummarizer.class)
    MemorySummarizeService memorySummarizeService(
            MemoryPort memory,
            MemorySummarizer summarizer,
            @Value("${dream.memory.soft-max-chars:3000}") int softMaxChars,
            @Value("${dream.memory.summary-target-chars:1200}") int summaryTargetChars) {
        return new MemorySummarizeService(memory, summarizer, softMaxChars, summaryTargetChars);
    }
}
