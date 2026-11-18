package ru.posts.ord.reporting.config;

import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import ru.posts.ord.reporting.OrdApiKeyResolver;
import ru.posts.ord.reporting.OrdChannelTokenResolver;
import ru.posts.ord.reporting.OrdWorkspaceApiKeyResolver;
import ru.posts.persistence.repository.WorkspaceDataKeyRepository;
import ru.posts.security.crypto.TokenCryptoService;

/**
 * ORD reporting wiring. Services under {@code ru.posts.ord.reporting} are discovered by app {@code
 * scanBasePackages = "ru.posts"}; this auto-config only registers properties and beans that need
 * {@code @ConditionalOnBean} (which is unreliable on {@code @Component} during component scanning).
 */
@AutoConfiguration(
        afterName = {
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
        })
@Profile("!openapi")
@EnableConfigurationProperties(OrdReportingProperties.class)
public class OrdReportingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OrdChannelTokenResolver.class)
    OrdChannelTokenResolver noopChannelTokenResolver() {
        return (UUID workspaceId, UUID channelId) -> Optional.empty();
    }

    /**
     * Registered here (not as {@code @Component}) so {@code @ConditionalOnBean(TokenCryptoService)}
     * evaluates after user {@code @Configuration} beans — apps scan {@code ru.posts} too early for
     * that condition on a component.
     */
    @Bean
    @ConditionalOnBean(TokenCryptoService.class)
    @ConditionalOnMissingBean(OrdApiKeyResolver.class)
    OrdApiKeyResolver ordApiKeyResolver(TokenCryptoService crypto, WorkspaceDataKeyRepository dataKeyRepository) {
        return new OrdWorkspaceApiKeyResolver(crypto, dataKeyRepository);
    }
}
