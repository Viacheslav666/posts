package ru.posts.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import ru.posts.api.notification.NotificationProperties;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {}
