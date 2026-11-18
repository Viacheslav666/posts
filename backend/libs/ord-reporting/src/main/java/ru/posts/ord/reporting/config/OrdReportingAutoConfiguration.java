package ru.posts.ord.reporting.config;

import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import ru.posts.ord.reporting.OrdChannelTokenResolver;

@AutoConfiguration
@Profile("!openapi")
@EnableConfigurationProperties(OrdReportingProperties.class)
@ComponentScan(basePackages = "ru.posts.ord.reporting")
public class OrdReportingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OrdChannelTokenResolver.class)
    OrdChannelTokenResolver noopChannelTokenResolver() {
        return (UUID workspaceId, UUID channelId) -> Optional.empty();
    }
}
