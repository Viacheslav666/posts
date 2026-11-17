package ru.posts.social.ok.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import ru.posts.social.api.SocialAdapter;
import ru.posts.social.ok.OkAdapterProperties;
import ru.posts.social.ok.OkSocialAdapter;

@AutoConfiguration
@ConditionalOnProperty(name = "posts.publisher.network", havingValue = "ok")
@EnableConfigurationProperties(OkAdapterProperties.class)
public class OkSocialAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SocialAdapter.class)
    SocialAdapter okSocialAdapter(OkAdapterProperties properties, ObjectMapper objectMapper) {
        return new OkSocialAdapter(properties, objectMapper);
    }
}
