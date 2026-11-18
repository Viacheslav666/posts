package ru.posts.grabber;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import ru.posts.grabber.config.GrabberProperties;
import ru.posts.grabber.ssrf.HostResolver;
import ru.posts.grabber.ssrf.SsrfProperties;
import ru.posts.grabber.token.GrabberTokenProvider;
import ru.posts.grabber.token.StaticGrabberTokenProvider;

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
    GrabberTokenProvider grabberTokenProvider(
            @org.springframework.beans.factory.annotation.Value("${posts.grabber.vk.access-token:test-token}")
                    String token) {
        return new StaticGrabberTokenProvider(token);
    }

    @Bean
    @ConditionalOnMissingBean(HostResolver.class)
    HostResolver grabberHostResolver() {
        return HostResolver.SYSTEM;
    }
}
