package ru.posts.persistence.config;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.posts.persistence.jdbc.PublicationDispatcherJdbc;
import ru.posts.persistence.jdbc.PublicationLifecycleJdbc;

/**
 * Registers hot-path JDBC beans. Entity/repository discovery is handled by the consuming app via
 * {@code @SpringBootApplication(scanBasePackages = "ru.posts")} / {@code AutoConfigurationPackages}.
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass(JpaRepository.class)
@ConditionalOnBean(DataSource.class)
public class PersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PublicationDispatcherJdbc.class)
    PublicationDispatcherJdbc publicationDispatcherJdbc(DataSource dataSource) {
        return new PublicationDispatcherJdbc(JdbcClient.create(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean(PublicationLifecycleJdbc.class)
    PublicationLifecycleJdbc publicationLifecycleJdbc(DataSource dataSource) {
        return new PublicationLifecycleJdbc(JdbcClient.create(dataSource));
    }
}
