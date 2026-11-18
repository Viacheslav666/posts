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
import ru.posts.persistence.jdbc.AutomationRuleClaimJdbc;
import ru.posts.persistence.jdbc.ChannelMediaCacheJdbc;
import ru.posts.persistence.jdbc.MediaAssetLoadJdbc;
import ru.posts.persistence.jdbc.OutboxJdbc;
import ru.posts.persistence.jdbc.OutboxRelayJdbc;
import ru.posts.persistence.jdbc.PublicationDispatcherJdbc;
import ru.posts.persistence.jdbc.PublicationLifecycleJdbc;
import ru.posts.persistence.jdbc.ScheduledActionDispatcherJdbc;
import ru.posts.persistence.jdbc.ScheduledActionLifecycleJdbc;

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

    @Bean
    @ConditionalOnMissingBean(ScheduledActionDispatcherJdbc.class)
    ScheduledActionDispatcherJdbc scheduledActionDispatcherJdbc(DataSource dataSource) {
        return new ScheduledActionDispatcherJdbc(JdbcClient.create(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean(ScheduledActionLifecycleJdbc.class)
    ScheduledActionLifecycleJdbc scheduledActionLifecycleJdbc(DataSource dataSource) {
        return new ScheduledActionLifecycleJdbc(JdbcClient.create(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean(OutboxJdbc.class)
    OutboxJdbc outboxJdbc(DataSource dataSource) {
        return new OutboxJdbc(JdbcClient.create(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean(OutboxRelayJdbc.class)
    OutboxRelayJdbc outboxRelayJdbc(DataSource dataSource) {
        return new OutboxRelayJdbc(JdbcClient.create(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean(AutomationRuleClaimJdbc.class)
    AutomationRuleClaimJdbc automationRuleClaimJdbc(DataSource dataSource) {
        return new AutomationRuleClaimJdbc(JdbcClient.create(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean(ChannelMediaCacheJdbc.class)
    ChannelMediaCacheJdbc channelMediaCacheJdbc(DataSource dataSource) {
        return new ChannelMediaCacheJdbc(JdbcClient.create(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean(MediaAssetLoadJdbc.class)
    MediaAssetLoadJdbc mediaAssetLoadJdbc(DataSource dataSource) {
        return new MediaAssetLoadJdbc(JdbcClient.create(dataSource));
    }
}
