package ru.posts.social.max.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import ru.posts.social.api.SocialAdapter;
import ru.posts.social.api.SocialModerationPort;
import ru.posts.social.max.MaxAdapterProperties;
import ru.posts.social.max.MaxSocialAdapter;
import ru.posts.social.max.MaxSocialModerationAdapter;

@AutoConfiguration
@ConditionalOnProperty(name = "posts.publisher.network", havingValue = "max")
@EnableConfigurationProperties(MaxAdapterProperties.class)
public class MaxSocialAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SocialAdapter.class)
    SocialAdapter maxSocialAdapter(MaxAdapterProperties properties) {
        return new MaxSocialAdapter(properties);
    }

    @Bean
    @ConditionalOnMissingBean(SocialModerationPort.class)
    SocialModerationPort maxSocialModerationPort(MaxAdapterProperties properties, ObjectMapper objectMapper) {
        return new MaxSocialModerationAdapter(properties, objectMapper);
    }
}
