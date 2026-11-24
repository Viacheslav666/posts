package ru.posts.security.oauth;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;
import ru.posts.persistence.repository.SocialAccountRepository;
import ru.posts.persistence.repository.WorkspaceDataKeyRepository;
import ru.posts.security.crypto.TokenCryptoService;

@AutoConfiguration
@Profile("!openapi")
@EnableConfigurationProperties(VkChannelConnectProperties.class)
@ConditionalOnBean({SocialAccountRepository.class, WorkspaceDataKeyRepository.class, TokenCryptoService.class})
public class VkIdTokenRefreshConfiguration {

    @Bean
    VkIdUserTokenRefresher vkIdUserTokenRefresher(
            VkChannelConnectProperties properties,
            SocialAccountRepository accounts,
            WorkspaceDataKeyRepository keys,
            TokenCryptoService crypto) {
        return new VkIdUserTokenRefresher(
                properties, accounts, keys, crypto, RestClient.builder().build());
    }
}
