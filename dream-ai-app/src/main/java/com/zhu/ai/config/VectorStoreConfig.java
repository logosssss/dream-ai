package com.zhu.ai.config;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

/**
 * 向量库专用 Postgres。主库见 {@link PersistenceConfig}；不能让 pgvector 抢 MySQL。
 * <p>
 * 不用第二份 {@code DataSourceProperties}（会和 {@code spring.datasource} 抢注入），
 * 用 {@link DataSourceBuilder#url} 写入，避免 Hikari 只有 driver、没有 jdbcUrl。
 */
@Configuration
@Profile("!test")
public class VectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    @Bean
    DataSource vectorDataSource(
            @Value("${dream.vector.datasource.url}") String url,
            @Value("${dream.vector.datasource.username}") String username,
            @Value("${dream.vector.datasource.password:}") String password,
            @Value("${dream.vector.datasource.driver-class-name}") String driverClassName) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("dream.vector.datasource.url is required");
        }
        return DataSourceBuilder.create()
                .driverClassName(driverClassName)
                .url(url)
                .username(username)
                .password(password)
                .build();
    }

    @Bean
    JdbcTemplate vectorJdbcTemplate(@Qualifier("vectorDataSource") DataSource vectorDataSource) {
        return new JdbcTemplate(vectorDataSource);
    }

    @Bean
    VectorStore vectorStore(
            @Qualifier("vectorJdbcTemplate") JdbcTemplate vectorJdbcTemplate,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.pgvector.dimensions:1024}") int dimensions) {
        log.info("VectorStore: PgVectorStore (postgres) dimensions={}", dimensions);
        return PgVectorStore.builder(vectorJdbcTemplate, embeddingModel)
                .dimensions(dimensions)
                .initializeSchema(true)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .build();
    }
}
