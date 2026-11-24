package ru.posts.ingest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.posts.grabber.GrabberAutoConfiguration;
import ru.posts.grabber.token.GrabberTokenProvider;
import ru.posts.grabber.token.VaultGrabberTokenProvider;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.SocialAccountRepository;
import ru.posts.persistence.repository.WorkspaceDataKeyRepository;
import ru.posts.security.crypto.TokenCryptoService;
import ru.posts.security.oauth.VkIdTokenRefreshConfiguration;
import ru.posts.security.oauth.VkIdUserTokenRefresher;

class IngestVkTokenWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(IngestCryptoConfig.class, RepositoryStubs.class)
            .withConfiguration(
                    AutoConfigurations.of(VkIdTokenRefreshConfiguration.class, GrabberAutoConfiguration.class))
            .withPropertyValues("posts.crypto.kek=local-dev-posts-kek", "posts.grabber.vk.access-token=test-token");

    @Test
    void wiresVaultProviderWithVkIdRefresher() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(TokenCryptoService.class);
            assertThat(ctx).hasSingleBean(VkIdUserTokenRefresher.class);
            assertThat(ctx).hasSingleBean(GrabberTokenProvider.class);
            GrabberTokenProvider provider = ctx.getBean(GrabberTokenProvider.class);
            assertThat(provider).isInstanceOf(VaultGrabberTokenProvider.class);
            assertThat(refresherOf((VaultGrabberTokenProvider) provider))
                    .isSameAs(ctx.getBean(VkIdUserTokenRefresher.class));
        });
    }

    @Test
    void startsWhenKekEnvIsUnset() {
        new ApplicationContextRunner()
                .withUserConfiguration(IngestCryptoConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(TokenCryptoService.class);
                });
    }

    private static VkIdUserTokenRefresher refresherOf(VaultGrabberTokenProvider provider) throws Exception {
        Field field = VaultGrabberTokenProvider.class.getDeclaredField("vkTokenRefresher");
        field.setAccessible(true);
        return (VkIdUserTokenRefresher) field.get(provider);
    }

    @Configuration
    static class RepositoryStubs {
        @Bean
        ChannelRepository channelRepository() {
            return mock(ChannelRepository.class);
        }

        @Bean
        SocialAccountRepository socialAccountRepository() {
            return mock(SocialAccountRepository.class);
        }

        @Bean
        WorkspaceDataKeyRepository workspaceDataKeyRepository() {
            return mock(WorkspaceDataKeyRepository.class);
        }
    }
}
