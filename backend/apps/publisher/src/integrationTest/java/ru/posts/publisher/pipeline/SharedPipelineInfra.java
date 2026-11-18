package ru.posts.publisher.pipeline;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import ru.posts.testsupport.emulator.SocialEmulatorProcess;

/**
 * JVM-singleton containers + social-emulator for publisher pipeline ITs.
 *
 * <p>Postgres/Redis/emulator are shared. RabbitMQ is <strong>per publisher network</strong> so a
 * cached VK/OK Spring context cannot drain {@code publish.vk}/{@code publish.ok} while TG ITs
 * assert queue isolation (TC-PUB-11).
 */
final class SharedPipelineInfra {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    /** Rabbit for {@code posts.publisher.network=tg} contexts only. */
    static final RabbitMQContainer RABBIT_TG =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    /** Isolated broker for VK story/publisher ITs. */
    static final RabbitMQContainer RABBIT_VK =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    /** Isolated broker for OK Moments/publisher ITs. */
    static final RabbitMQContainer RABBIT_OK =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static SocialEmulatorProcess emulator;
    private static final Object EMULATOR_LOCK = new Object();

    static {
        POSTGRES.start();
        RABBIT_TG.start();
        RABBIT_VK.start();
        RABBIT_OK.start();
        REDIS.start();
    }

    private SharedPipelineInfra() {}

    static SocialEmulatorProcess emulator() {
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

    static String findRepoRoot() {
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
