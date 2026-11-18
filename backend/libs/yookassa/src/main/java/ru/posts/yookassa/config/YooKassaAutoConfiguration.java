package ru.posts.yookassa.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import ru.posts.yookassa.YooKassaClient;
import ru.posts.yookassa.YooKassaIpValidator;
import ru.posts.yookassa.YooKassaProperties;

@AutoConfiguration
@EnableConfigurationProperties(YooKassaProperties.class)
public class YooKassaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    YooKassaClient yooKassaClient(YooKassaProperties properties, ObjectMapper objectMapper) {
        return new YooKassaClient(properties, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    YooKassaIpValidator yooKassaIpValidator(YooKassaProperties properties) {
        return new YooKassaIpValidator(properties.webhookIpCidrs());
    }
}
