package ru.posts.suggest;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import ru.posts.suggest.sync.SuggestSyncService.SuggestTokenResolver;

/**
 * Registers BE-34 suggest automation fallback beans. Concrete {@code @Service}/{@code @Component}
 * classes under {@code ru.posts.suggest} are picked up by apps that scan {@code ru.posts}.
 */
@AutoConfiguration
@Profile("!openapi")
public class AutomationSuggestAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SuggestTokenResolver.class)
    SuggestTokenResolver suggestTokenResolver() {
        return SuggestTokenResolver.noop();
    }
}
