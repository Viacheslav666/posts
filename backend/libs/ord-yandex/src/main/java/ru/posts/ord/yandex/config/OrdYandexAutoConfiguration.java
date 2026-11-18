package ru.posts.ord.yandex.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import ru.posts.ord.api.OrdProvider;
import ru.posts.ord.yandex.OrdYandexProperties;
import ru.posts.ord.yandex.YandexOrdProvider;

@AutoConfiguration
@EnableConfigurationProperties(OrdYandexProperties.class)
public class OrdYandexAutoConfiguration {

    @Bean(name = "yandexOrdProvider")
    @ConditionalOnMissingBean(name = "yandexOrdProvider")
    OrdProvider yandexOrdProvider(OrdYandexProperties properties, ObjectMapper objectMapper) {
        return new YandexOrdProvider(properties, objectMapper);
    }
}
