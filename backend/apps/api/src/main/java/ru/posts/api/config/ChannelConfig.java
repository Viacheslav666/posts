package ru.posts.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.posts.api.channel.ChannelProperties;
import ru.posts.security.crypto.TokenCryptoService;

@Configuration
@EnableConfigurationProperties(ChannelProperties.class)
public class ChannelConfig {

    @Bean
    @Profile("!openapi")
    TokenCryptoService tokenCryptoService(@Value("${posts.crypto.kek:local-dev-posts-kek}") String kek) {
        return TokenCryptoService.fromKeyMaterial(kek);
    }
}
