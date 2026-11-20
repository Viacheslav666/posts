package ru.posts.publisher;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.posts.publisher.config.PublisherCryptoConfig;
import ru.posts.security.crypto.TokenCryptoService;

/**
 * Unit CI guard: one {@link TokenCryptoService} bean in publisher config (no {@code BeanDefinitionOverrideException}
 * in compose / prod).
 */
class PublisherApplicationContextSmokeTest {

    @Test
    void singleTokenCryptoServiceBeanInPublisherCryptoConfig() {
        new ApplicationContextRunner()
                .withUserConfiguration(PublisherCryptoConfig.class)
                .withPropertyValues("spring.main.allow-bean-definition-overriding=false")
                .run(ctx -> assertThat(ctx).hasSingleBean(TokenCryptoService.class));
    }
}
