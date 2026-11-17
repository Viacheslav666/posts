package ru.posts.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.posts.api.channel.ChannelProperties;
import ru.posts.api.channel.IngestPublisher;
import ru.posts.api.channel.LoggingIngestPublisher;
import ru.posts.security.crypto.TokenCryptoService;

@Configuration
@EnableConfigurationProperties(ChannelProperties.class)
public class ChannelConfig {

    @Bean
    @Profile("!openapi")
    TokenCryptoService tokenCryptoService(@Value("${posts.crypto.kek:local-dev-posts-kek}") String kek) {
        return TokenCryptoService.fromKeyMaterial(kek);
    }

    @Bean
    @Profile("!openapi")
    @ConditionalOnMissingBean(IngestPublisher.class)
    IngestPublisher ingestPublisher() {
        return new LoggingIngestPublisher();
    }
}
