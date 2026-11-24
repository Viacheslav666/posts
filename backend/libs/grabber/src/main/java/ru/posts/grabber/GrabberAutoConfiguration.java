package ru.posts.grabber;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import ru.posts.grabber.ai.GrabberRewriteProvider;
import ru.posts.grabber.ai.StubGrabberRewriteProvider;
import ru.posts.grabber.config.GrabberProperties;
import ru.posts.grabber.ssrf.HostResolver;
import ru.posts.grabber.ssrf.SsrfProperties;
import ru.posts.grabber.token.GrabberTokenProvider;
import ru.posts.grabber.token.StaticGrabberTokenProvider;
import ru.posts.grabber.token.VaultGrabberTokenProvider;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.SocialAccountRepository;
import ru.posts.persistence.repository.WorkspaceDataKeyRepository;
import ru.posts.security.crypto.TokenCryptoService;
import ru.posts.security.oauth.VkIdUserTokenRefresher;

/**
 * Registers fallback beans only. Concrete {@code @Service}/{@code @Component} classes under {@code
 * ru.posts.grabber} are picked up by apps that scan {@code ru.posts}.
 */
@AutoConfiguration
@Profile("!openapi")
@EnableConfigurationProperties({SsrfProperties.class, GrabberProperties.class})
public class GrabberAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GrabberTokenProvider.class)
    @ConditionalOnBean({
        TokenCryptoService.class,
        ChannelRepository.class,
        SocialAccountRepository.class,
        WorkspaceDataKeyRepository.class
    })
    GrabberTokenProvider vaultGrabberTokenProvider(
            @Value("${posts.grabber.vk.access-token:test-token}") String token,
            ChannelRepository channels,
            SocialAccountRepository socialAccounts,
            WorkspaceDataKeyRepository keys,
            TokenCryptoService crypto,
            ObjectProvider<VkIdUserTokenRefresher> vkTokenRefresher) {
        return new VaultGrabberTokenProvider(
                token, channels, socialAccounts, keys, crypto, vkTokenRefresher.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(GrabberTokenProvider.class)
    GrabberTokenProvider grabberTokenProvider(@Value("${posts.grabber.vk.access-token:test-token}") String token) {
        return new StaticGrabberTokenProvider(token);
    }

    @Bean
    @ConditionalOnMissingBean(HostResolver.class)
    HostResolver grabberHostResolver() {
        return HostResolver.SYSTEM;
    }

    @Bean
    @ConditionalOnProperty(prefix = "posts.grabber.ai", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(GrabberRewriteProvider.class)
    GrabberRewriteProvider grabberRewriteProvider(GrabberProperties properties) {
        return new StubGrabberRewriteProvider(properties);
    }
}
