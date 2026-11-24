package ru.posts.ingest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.posts.security.crypto.TokenCryptoService;

@Configuration
public class IngestCryptoConfig {

    @Bean
    TokenCryptoService tokenCryptoService(@Value("${posts.crypto.kek:local-dev-posts-kek}") String kek) {
        return TokenCryptoService.fromKeyMaterial(kek);
    }
}
