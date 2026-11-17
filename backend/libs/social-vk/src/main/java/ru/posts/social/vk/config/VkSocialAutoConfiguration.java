package ru.posts.social.vk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import ru.posts.social.api.SocialAdapter;
import ru.posts.social.vk.VkAdapterProperties;
import ru.posts.social.vk.VkSocialAdapter;

@AutoConfiguration
@ConditionalOnProperty(name = "posts.publisher.network", havingValue = "vk")
@EnableConfigurationProperties(VkAdapterProperties.class)
public class VkSocialAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SocialAdapter.class)
    SocialAdapter vkSocialAdapter(VkAdapterProperties properties, ObjectMapper objectMapper) {
        return new VkSocialAdapter(properties, objectMapper);
    }
}
