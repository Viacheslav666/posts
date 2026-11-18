package ru.posts.analytics.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;

/**
 * Analytics wiring (BE-30). {@link ru.posts.analytics.AnalyticsPostsAggregator} is component-scanned
 * via app {@code scanBasePackages = "ru.posts"}; this config only enables properties.
 */
@AutoConfiguration
@Profile("!openapi")
@EnableConfigurationProperties(AnalyticsProperties.class)
public class AnalyticsAutoConfiguration {}
