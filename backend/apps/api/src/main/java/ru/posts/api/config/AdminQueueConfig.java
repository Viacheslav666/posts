package ru.posts.api.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.posts.api.admin.AdminQueueService;
import ru.posts.api.admin.InMemoryAdminQueueService;
import ru.posts.api.admin.RabbitAdminQueueService;
import ru.posts.persistence.repository.AdminIdempotencyRepository;

/**
 * Registers exactly one {@link AdminQueueService}: Rabbit when a {@link RabbitTemplate} exists,
 * otherwise the in-memory fallback (tests / environments without AMQP).
 */
@Configuration
@Profile("!openapi")
public class AdminQueueConfig {

    @Bean
    @ConditionalOnBean(RabbitTemplate.class)
    @ConditionalOnMissingBean(AdminQueueService.class)
    AdminQueueService rabbitAdminQueueService(
            RabbitTemplate rabbitTemplate,
            ConnectionFactory connectionFactory,
            AdminIdempotencyRepository adminIdempotencyRepository) {
        return new RabbitAdminQueueService(rabbitTemplate, connectionFactory, adminIdempotencyRepository);
    }

    @Bean
    @ConditionalOnMissingBean(AdminQueueService.class)
    InMemoryAdminQueueService inMemoryAdminQueueService(AdminIdempotencyRepository adminIdempotencyRepository) {
        return new InMemoryAdminQueueService(adminIdempotencyRepository);
    }
}
