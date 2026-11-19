package ru.posts.social.telegram.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import ru.posts.social.api.SocialAdapter;
import ru.posts.social.api.SocialModerationPort;
import ru.posts.social.telegram.TelegramAdapterProperties;
import ru.posts.social.telegram.TelegramSocialAdapter;
import ru.posts.social.telegram.TelegramSocialModerationAdapter;

@AutoConfiguration
@ConditionalOnProperty(name = "posts.publisher.network", havingValue = "tg", matchIfMissing = true)
@EnableConfigurationProperties(TelegramAdapterProperties.class)
public class TelegramSocialAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SocialAdapter.class)
    SocialAdapter telegramSocialAdapter(TelegramAdapterProperties properties, ObjectMapper objectMapper) {
        return new TelegramSocialAdapter(properties, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(SocialModerationPort.class)
    SocialModerationPort telegramSocialModerationPort(TelegramAdapterProperties properties, ObjectMapper objectMapper) {
        return new TelegramSocialModerationAdapter(properties, objectMapper);
    }
}
