package ru.posts.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.posts.api.auth.mail.LoggingAuthMailSender;
import ru.posts.api.config.S3Properties;
import ru.posts.persistence.entity.IdempotencyRequestEntity;
import ru.posts.persistence.repository.IdempotencyRequestRepository;
import ru.posts.persistence.repository.MediaAssetRepository;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class MediaIdempotencyIntegrationTest {

    private static final Pattern TOKEN_IN_BODY = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Container
    static GenericContainer<?> s3mock = new GenericContainer<>(DockerImageName.parse("adobe/s3mock:3.12.0"))
            .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", "posts-media")
            .withExposedPorts(9090)
            .waitingFor(Wait.forHttp("/").forPort(9090).forStatusCode(200));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("posts.s3.endpoint", () -> "http://" + s3mock.getHost() + ":" + s3mock.getMappedPort(9090));
        registry.add("posts.s3.bucket", () -> "posts-media");
        registry.add("posts.s3.access-key", () -> "posts");
        registry.add("posts.s3.secret-key", () -> "postsposts");
        registry.add("posts.s3.path-style-access", () -> "true");
        registry.add("posts.s3.region", () -> "us-east-1");
        registry.add("posts.idempotency.in-flight-wait", () -> "3s");
        registry.add("posts.idempotency.in-flight-poll", () -> "25ms");
        registry.add(
                "spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LoggingAuthMailSender mailSender;

    @Autowired
    MediaAssetRepository mediaAssetRepository;

    @Autowired
    IdempotencyRequestRepository idempotencyRequestRepository;

    @Autowired
    S3Client s3Client;

    @Autowired
    S3Properties s3Properties;

    @BeforeEach
    void setUp() {
        mailSender.clear();
        try {
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .build());
        } catch (Exception ignored) {
            // bucket may already exist
        }
    }

    @Test
    void sameKeyAndBodyReplaysCreateUploadResponse() throws Exception {
        String token = registerVerifyLogin("idem-same-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        String key = "upload-" + UUID.randomUUID();
        String body = objectMapper.writeValueAsString(Map.of(
                "workspaceId", workspaceId.toString(),
                "kind", "image",
                "mime", "image/jpeg",
                "sizeBytes", 1024,
                "filename", "a.jpg"));

        JsonNode first = readJson(mockMvc.perform(post("/api/v1/media/uploads")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn());

        JsonNode second = readJson(mockMvc.perform(post("/api/v1/media/uploads")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(second.get("mediaId").asText())
                .isEqualTo(first.get("mediaId").asText());
        assertThat(second.get("uploadUrl").asText())
                .isEqualTo(first.get("uploadUrl").asText());
        assertThat(mediaAssetRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId))
                .hasSize(1);
    }

    @Test
    void sameKeyDifferentBodyReturns409() throws Exception {
        String token = registerVerifyLogin("idem-diff-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        String key = "upload-diff-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/media/uploads")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "kind", "image",
                                "mime", "image/jpeg",
                                "sizeBytes", 1024,
                                "filename", "a.jpg"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/media/uploads")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "kind", "image",
                                "mime", "image/jpeg",
                                "sizeBytes", 2048,
                                "filename", "b.jpg"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("idempotency_conflict"));
    }

    @Test
    void parallelSameKeyReturnsSameMediaId() throws Exception {
        String token = registerVerifyLogin("idem-race-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        String key = "upload-race-" + UUID.randomUUID();
        String body = objectMapper.writeValueAsString(Map.of(
                "workspaceId", workspaceId.toString(),
                "kind", "image",
                "mime", "image/jpeg",
                "sizeBytes", 512,
                "filename", "race.jpg"));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicReference<Exception> error = new AtomicReference<>();
        Future<String> f1 = pool.submit(() -> performUpload(token, key, body, start, error));
        Future<String> f2 = pool.submit(() -> performUpload(token, key, body, start, error));
        start.countDown();
        String mediaId1 = f1.get(20, TimeUnit.SECONDS);
        String mediaId2 = f2.get(20, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(error.get()).isNull();
        assertThat(mediaId1).isEqualTo(mediaId2);
        assertThat(mediaAssetRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId))
                .hasSize(1);
    }

    @Test
    void expiredKeyCanBeReused() throws Exception {
        String token = registerVerifyLogin("idem-ttl-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID userId = userIdFromMe(token);
        UUID workspaceId = firstWorkspaceId(token);
        String key = "upload-ttl-" + UUID.randomUUID();

        JsonNode first = readJson(mockMvc.perform(post("/api/v1/media/uploads")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "kind", "image",
                                "mime", "image/jpeg",
                                "sizeBytes", 100,
                                "filename", "old.jpg"))))
                .andExpect(status().isCreated())
                .andReturn());

        IdempotencyRequestEntity row =
                idempotencyRequestRepository.findByKeyAndUserId(key, userId).orElseThrow();
        row.setExpiresAt(Instant.now().minusSeconds(60));
        idempotencyRequestRepository.save(row);

        JsonNode second = readJson(mockMvc.perform(post("/api/v1/media/uploads")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "kind", "image",
                                "mime", "image/jpeg",
                                "sizeBytes", 100,
                                "filename", "new.jpg"))))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(second.get("mediaId").asText())
                .isNotEqualTo(first.get("mediaId").asText());
        assertThat(mediaAssetRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId))
                .hasSize(2);
    }

    @Test
    void importUrlValidationFailureAbandonsKeyForRetry() throws Exception {
        String token = registerVerifyLogin("idem-import-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        String key = "import-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/media/import-url")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("workspaceId", workspaceId.toString(), "url", "http://127.0.0.1/a.jpg"))))
                .andExpect(status().isBadRequest());

        // Claim abandoned after failure — same key can be retried (same or different body).
        mockMvc.perform(post("/api/v1/media/import-url")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("workspaceId", workspaceId.toString(), "url", "http://127.0.0.1/b.jpg"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sameKeyOnImportAfterUploadConflicts() throws Exception {
        String token = registerVerifyLogin("idem-cross-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        String key = "shared-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/media/uploads")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "kind", "image",
                                "mime", "image/jpeg",
                                "sizeBytes", 100,
                                "filename", "x.jpg"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/media/import-url")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("workspaceId", workspaceId.toString(), "url", "http://127.0.0.1/x.jpg"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("idempotency_conflict"));
    }

    private String performUpload(
            String token, String key, String body, CountDownLatch start, AtomicReference<Exception> error)
            throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            MvcResult result = mockMvc.perform(post("/api/v1/media/uploads")
                            .with(csrf())
                            .header("Authorization", bearer(token))
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();
            int status = result.getResponse().getStatus();
            if (status != 201) {
                throw new IllegalStateException("expected 201, got " + status + ": "
                        + result.getResponse().getContentAsString());
            }
            return objectMapper
                    .readTree(result.getResponse().getContentAsString())
                    .get("mediaId")
                    .asText();
        } catch (Exception ex) {
            error.compareAndSet(null, ex);
            throw ex;
        }
    }

    private UUID userIdFromMe(String token) throws Exception {
        JsonNode me = readJson(
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/me")
                                .header("Authorization", bearer(token)))
                        .andReturn());
        return UUID.fromString(me.get("id").asText());
    }

    private UUID firstWorkspaceId(String accessToken) throws Exception {
        JsonNode list = readJson(mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/workspaces")
                                .header("Authorization", bearer(accessToken)))
                .andReturn());
        return UUID.fromString(list.get("items").get(0).get("id").asText());
    }

    private String registerVerifyLogin(String email, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery", "name", name))))
                .andExpect(status().isCreated());
        String verifyToken = latestMailToken(email);
        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", verifyToken))))
                .andExpect(status().isOk());
        JsonNode login = readJson(mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery"))))
                .andExpect(status().isOk())
                .andReturn());
        return login.get("accessToken").asText();
    }

    private String latestMailToken(String email) {
        return mailSender.messages().stream()
                .filter(m -> m.to().equalsIgnoreCase(email))
                .reduce((a, b) -> b)
                .map(m -> {
                    Matcher matcher = TOKEN_IN_BODY.matcher(m.body());
                    assertThat(matcher.find()).isTrue();
                    return matcher.group(1);
                })
                .orElseThrow();
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
