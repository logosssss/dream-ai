package com.zhu.ai.config;

import com.zhu.ai.conversation.InMemoryConversationPort;
import com.zhu.ai.conversation.MyBatisConversationPort;
import com.zhu.ai.knowledge.InMemoryKeywordIndex;
import com.zhu.ai.knowledge.PgVectorRetrievePort;
import com.zhu.ai.kernel.conversation.ConversationPort;
import com.zhu.ai.kernel.knowledge.RetrievePort;
import com.zhu.ai.kernel.memory.MemoryPort;
import com.zhu.ai.kernel.observe.ObservePort;
import com.zhu.ai.memory.InMemoryMemoryPort;
import com.zhu.ai.memory.RedisMemoryPort;
import com.zhu.ai.observe.InMemoryObservePort;
import com.zhu.ai.service.ConversationTurnService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Kernel Port 接线：会话 / 记忆 / 检索 / 观测。
 * 创建期用 {@link ObjectProvider} 选择实现，避免 {@code @ConditionalOnBean} 问早了。
 */
@Configuration
public class PortsConfig {

    static final String INTRO_RESOURCE = "/rag/intro.txt";

    static final String VECTOR_TABLE = "vector_store";

    private static final Logger log = LoggerFactory.getLogger(PortsConfig.class);

    // --- 会话 ---

    @Bean
    ConversationPort conversationPort(ObjectProvider<ConversationTurnService> services) {
        ConversationTurnService service = services.getIfAvailable();
        if (service != null) {
            log.info("ConversationPort: MyBatisConversationPort");
            return new MyBatisConversationPort(service);
        }
        log.info("ConversationPort: InMemoryConversationPort (no ConversationTurnService)");
        return new InMemoryConversationPort();
    }

    // --- 记忆 ---

    @Bean
    MemoryPort memoryPort(ObjectProvider<StringRedisTemplate> redis) {
        StringRedisTemplate template = redis.getIfAvailable();
        if (template != null) {
            log.info("MemoryPort: RedisMemoryPort");
            return new RedisMemoryPort(template);
        }
        log.info("MemoryPort: InMemoryMemoryPort (no StringRedisTemplate)");
        return new InMemoryMemoryPort();
    }

    // --- 检索 ---

    @Bean
    RetrievePort retrievePort(
            ObjectProvider<VectorStore> stores,
            @Qualifier("vectorJdbcTemplate") ObjectProvider<JdbcTemplate> vectorJdbc) {
        VectorStore store = stores.getIfAvailable();
        if (store != null) {
            ingestIntro(store, vectorJdbc.getIfAvailable());
            log.info("RetrievePort: PgVectorRetrievePort");
            return new PgVectorRetrievePort(store);
        }
        InMemoryKeywordIndex index = new InMemoryKeywordIndex();
        index.ingest(readIntro());
        log.info("RetrievePort: InMemoryKeywordIndex (no VectorStore)");
        return index;
    }

    // --- 观测 ---

    @Bean
    ObservePort observePort() {
        return new InMemoryObservePort();
    }

    private static void ingestIntro(VectorStore store, JdbcTemplate jdbc) {
        if (jdbc != null) {
            Integer count = jdbc.queryForObject("select count(*) from " + VECTOR_TABLE, Integer.class);
            if (count != null && count > 0) {
                log.info("pgvector skip ingest, rows={}", count);
                return;
            }
        }
        List<String> chunks = InMemoryKeywordIndex.chunk(readIntro());
        List<Document> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            // PgVectorStore 的 id 必须是 UUID
            docs.add(new Document(
                    UUID.randomUUID().toString(),
                    chunks.get(i),
                    Map.of("source", "classpath:" + INTRO_RESOURCE, "chunk", String.valueOf(i))));
        }
        store.add(docs);
        log.info("pgvector ingested chunks={}", docs.size());
    }

    private static String readIntro() {
        try (InputStream in = PortsConfig.class.getResourceAsStream(INTRO_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing classpath resource " + INTRO_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
