package ru.posts.publisher.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableConfigurationProperties(PublisherProperties.class)
public class PublisherConfiguration {

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean(destroyMethod = "close")
    ExecutorService publisherVirtualExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    TaskExecutor rabbitListenerTaskExecutor(ExecutorService publisherVirtualExecutor) {
        return new TaskExecutorAdapter(publisherVirtualExecutor);
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            TaskExecutor rabbitListenerTaskExecutor,
            PublisherProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setTaskExecutor(rabbitListenerTaskExecutor);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(properties.prefetch());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(20)
                .minimumNumberOfCalls(5)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    TimeLimiterRegistry timeLimiterRegistry(@Value("${posts.publisher.publish-timeout:15s}") Duration publishTimeout) {
        return TimeLimiterRegistry.of(
                TimeLimiterConfig.custom().timeoutDuration(publishTimeout).build());
    }

    @Bean
    BulkheadRegistry bulkheadRegistry(@Value("${posts.publisher.bulkhead-max-concurrent:80}") int maxConcurrent) {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrent)
                .maxWaitDuration(Duration.ZERO)
                .build();
        return BulkheadRegistry.of(config);
    }

    @Bean
    CircuitBreaker socialCircuitBreaker(CircuitBreakerRegistry registry, PublisherProperties properties) {
        return registry.circuitBreaker("social-" + properties.network());
    }

    @Bean
    TimeLimiter socialTimeLimiter(TimeLimiterRegistry registry, PublisherProperties properties) {
        return registry.timeLimiter("social-" + properties.network());
    }

    @Bean
    Bulkhead socialBulkhead(BulkheadRegistry registry, PublisherProperties properties) {
        return registry.bulkhead("social-" + properties.network());
    }
}
