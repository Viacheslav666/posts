package ru.posts.ord.reporting.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.posts.ord.reporting.OrdApiKeyResolver;
import ru.posts.persistence.repository.WorkspaceDataKeyRepository;
import ru.posts.security.crypto.TokenCryptoService;

class OrdReportingAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OrdReportingAutoConfiguration.class));

    @Test
    void registersOrdApiKeyResolverWhenCryptoAndDekRepoPresent() {
        runner.withUserConfiguration(CryptoAndRepoConfig.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(OrdApiKeyResolver.class);
            assertThat(ctx).hasSingleBean(OrdReportingProperties.class);
        });
    }

    @Test
    void skipsOrdApiKeyResolverWithoutTokenCrypto() {
        runner.withUserConfiguration(RepoOnlyConfig.class)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(OrdApiKeyResolver.class));
    }

    @Configuration
    static class CryptoAndRepoConfig {
        @Bean
        TokenCryptoService tokenCryptoService() {
            return TokenCryptoService.fromKeyMaterial("local-dev-posts-kek-for-test");
        }

        @Bean
        WorkspaceDataKeyRepository workspaceDataKeyRepository() {
            return mock(WorkspaceDataKeyRepository.class);
        }
    }

    @Configuration
    static class RepoOnlyConfig {
        @Bean
        WorkspaceDataKeyRepository workspaceDataKeyRepository() {
            return mock(WorkspaceDataKeyRepository.class);
        }
    }
}
