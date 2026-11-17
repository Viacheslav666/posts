package ru.posts.publisher.pipeline;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import ru.posts.publisher.PublisherApplication;
import ru.posts.publisher.service.PublicationProcessor;
import ru.posts.testsupport.emulator.SocialEmulatorProcess;

/**
 * Shared pipeline IT base. Containers are started once as JVM singletons (not {@code @Container})
 * so multiple test classes / Spring contexts (e.g. rate-limit overrides) do not stop Postgres
 * between classes.
 */
@SpringBootTest(classes = PublisherApplication.class)
abstract class AbstractPipelineIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @ServiceConnection
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        POSTGRES.start();
        RABBIT.start();
        REDIS.start();
    }

    private static SocialEmulatorProcess emulator;
    private static final Object EMULATOR_LOCK = new Object();

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected TestDispatchHelper dispatchService;

    @Autowired
    protected PublicationProcessor publicationProcessor;

    @Autowired
    protected CircuitBreaker socialCircuitBreaker;

    protected static SocialEmulatorProcess emulator() {
        synchronized (EMULATOR_LOCK) {
            if (emulator == null) {
                System.setProperty("posts.repo.root", findRepoRoot());
                try {
                    emulator = SocialEmulatorProcess.start();
                    Runtime.getRuntime().addShutdownHook(new Thread(emulator::close));
                } catch (Exception ex) {
                    throw new IllegalStateException("Failed to start social-emulator", ex);
                }
            }
            return emulator;
        }
    }

    @BeforeEach
    void resetEmulator() throws Exception {
        emulator().reset();
        socialCircuitBreaker.reset();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("posts.ratelimit.redis-uri", () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        registry.add("posts.publisher.network", () -> "tg");
        registry.add("posts.publisher.pod-name", () -> "publisher-it");
        registry.add("posts.social.telegram.base-url", () -> emulator().baseUrl());
        registry.add("posts.social.telegram.read-timeout", () -> "5s");
        // High ceilings come from integrationTest application.yml; RateLimitPipelineIT lowers them.
        registry.add("posts.ratelimit.enabled", () -> "true");
        registry.add("posts.publisher.prefetch", () -> "200");
        registry.add("posts.publisher.publish-timeout", () -> "30s");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "40");
        registry.add("spring.rabbitmq.publisher-confirm-type", () -> "correlated");
        registry.add("spring.rabbitmq.publisher-returns", () -> "true");
        registry.add("server.port", () -> "0");
    }

    private static String findRepoRoot() {
        java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath();
        for (int i = 0; i < 8; i++) {
            if (java.nio.file.Files.isDirectory(dir.resolve("tests/social-emulator"))
                    && java.nio.file.Files.isDirectory(dir.resolve("backend"))) {
                return dir.toString();
            }
            dir = dir.getParent();
            if (dir == null) {
                break;
            }
        }
        java.nio.file.Path fromModule =
                java.nio.file.Path.of("../..").toAbsolutePath().normalize();
        if (java.nio.file.Files.isDirectory(fromModule.resolve("tests/social-emulator"))) {
            return fromModule.toString();
        }
        return java.nio.file.Path.of("").toAbsolutePath().toString();
    }
}
