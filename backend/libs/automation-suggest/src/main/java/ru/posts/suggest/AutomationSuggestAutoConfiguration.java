package ru.posts.suggest;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import ru.posts.persistence.repository.SocialAccountRepository;
import ru.posts.persistence.repository.WorkspaceDataKeyRepository;
import ru.posts.security.crypto.TokenCryptoService;
import ru.posts.suggest.sync.SuggestSyncService.SuggestTokenResolver;
import ru.posts.suggest.sync.VaultSuggestTokenResolver;

/**
 * Registers BE-34 suggest automation fallback beans. Concrete {@code @Service}/{@code @Component}
 * classes under {@code ru.posts.suggest} are picked up by apps that scan {@code ru.posts}.
 */
@AutoConfiguration
@Profile("!openapi")
public class AutomationSuggestAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SuggestTokenResolver.class)
    @ConditionalOnBean({TokenCryptoService.class, WorkspaceDataKeyRepository.class, SocialAccountRepository.class})
    SuggestTokenResolver vaultSuggestTokenResolver(
            SocialAccountRepository socialAccountRepository,
            WorkspaceDataKeyRepository keys,
            TokenCryptoService crypto) {
        return new VaultSuggestTokenResolver(socialAccountRepository, keys, crypto);
    }

    @Bean
    @ConditionalOnMissingBean(SuggestTokenResolver.class)
    SuggestTokenResolver suggestTokenResolver() {
        return SuggestTokenResolver.noop();
    }
}
