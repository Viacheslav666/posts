package ru.posts.api.config;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.posts.api.channel.ChannelProperties;
import ru.posts.api.channel.IngestPublisher;
import ru.posts.api.channel.LoggingIngestPublisher;
import ru.posts.api.channel.RabbitIngestPublisher;
import ru.posts.security.crypto.TokenCryptoService;

@Configuration
@EnableConfigurationProperties(ChannelProperties.class)
public class ChannelConfig {

    @Bean
    @Profile("!openapi")
    TokenCryptoService tokenCryptoService(@Value("${posts.crypto.kek:local-dev-posts-kek}") String kek) {
        return TokenCryptoService.fromKeyMaterial(kek);
    }

    /**
     * Prefer Rabbit when available. Avoid {@code @ConditionalOnBean(RabbitTemplate)} on a user
     * {@code @Configuration}: it is evaluated before Rabbit auto-config and silently falls back to
     * logging (hooks return 200 with empty ingest queues).
     */
    @Bean
    @Profile("!openapi")
    @ConditionalOnMissingBean(IngestPublisher.class)
    IngestPublisher ingestPublisher(ObjectProvider<RabbitTemplate> rabbitTemplate) {
        RabbitTemplate template = rabbitTemplate.getIfAvailable();
        if (template != null) {
            return new RabbitIngestPublisher(template);
        }
        return new LoggingIngestPublisher();
    }

    @Bean
    @Profile("openapi")
    @ConditionalOnMissingBean(IngestPublisher.class)
    IngestPublisher openApiLoggingIngestPublisher() {
        return new LoggingIngestPublisher();
    }
}
