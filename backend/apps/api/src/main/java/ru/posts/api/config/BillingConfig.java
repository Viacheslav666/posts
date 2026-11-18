package ru.posts.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import ru.posts.api.billing.BillingProperties;

@Configuration
@EnableConfigurationProperties(BillingProperties.class)
public class BillingConfig {}
