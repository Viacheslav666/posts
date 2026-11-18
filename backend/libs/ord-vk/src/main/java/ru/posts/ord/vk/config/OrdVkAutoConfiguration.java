package ru.posts.ord.vk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import ru.posts.ord.api.OrdProvider;
import ru.posts.ord.vk.OrdVkProperties;
import ru.posts.ord.vk.VkOrdProvider;

@AutoConfiguration
@EnableConfigurationProperties(OrdVkProperties.class)
public class OrdVkAutoConfiguration {

    @Bean(name = "vkOrdProvider")
    @ConditionalOnMissingBean(name = "vkOrdProvider")
    OrdProvider vkOrdProvider(OrdVkProperties properties, ObjectMapper objectMapper) {
        return new VkOrdProvider(properties, objectMapper);
    }
}
