package com.zhu.ai.config;

import javax.sql.DataSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * MySQL 主库 + MyBatis-Plus Mapper 扫描。
 * <p>
 * 必须显式声明 {@code @Primary} DataSource：一旦存在任意 {@link DataSource} Bean
 * （例如向量库），Boot 的 DataSource 自动配置会退让，主库就不会再从
 * {@code spring.datasource} 建出来。
 */
@Configuration
@Profile("!test")
@MapperScan("com.zhu.ai.persistence.mapper")
public class PersistenceConfig {

    @Bean
    @Primary
    DataSource dataSource(DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.initializeDataSourceBuilder().build();
    }
}
