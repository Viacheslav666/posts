package ru.posts.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
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
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.MediaAssetEntity;
import ru.posts.persistence.repository.MediaAssetRepository;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/** API upload/complete + SSRF/IDOR against MinIO/s3mock + Postgres + RabbitMQ. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class MediaApiIntegrationTest {

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
        // Keep Rabbit enabled for this IT (override test exclusion)
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
            // already exists
        }
    }

    @Test
    void createUploadPutAndComplete() throws Exception {
        String token = registerVerifyLogin("media-a-" + UUID.randomUUID() + "@example.com", "Owner A");
        UUID workspaceId = firstWorkspaceId(token);

        byte[] jpeg = minimalJpegBytes();
        JsonNode created = readJson(mockMvc.perform(post("/api/v1/media/uploads")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "kind", "image",
                                "mime", "image/jpeg",
                                "sizeBytes", jpeg.length,
                                "filename", "shot.jpg"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaId").exists())
                .andExpect(jsonPath("$.uploadUrl").exists())
                .andReturn());

        assertThat(created.path("uploadId").isNull()).isTrue();
        assertThat(created.path("parts").size()).isZero();
        assertThat(created.path("headers").path("Content-Type").asText()).isEqualTo("image/jpeg");

        String uploadUrl = created.get("uploadUrl").asText();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> put = client.send(
                HttpRequest.newBuilder(URI.create(uploadUrl))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(jpeg))
                        .header("Content-Type", "image/jpeg")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(put.statusCode()).isBetween(200, 299);

        UUID mediaId = UUID.fromString(created.get("mediaId").asText());
        mockMvc.perform(post("/api/v1/media/" + mediaId + "/complete")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processing"));
    }

    @Test
    void multipartPresignsEveryPartAndCompletes() throws Exception {
        String token = registerVerifyLogin("media-mpu-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        long size = S3MultipartUrls.PART_SIZE_BYTES + 1;
        JsonNode created = readJson(mockMvc.perform(post("/api/v1/media/uploads")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "kind", "video",
                                "mime", "video/mp4",
                                "sizeBytes", size,
                                "filename", "clip.mp4"))))
                .andExpect(status().isCreated())
                .andReturn());

        String uploadId = created.path("uploadId").asText();
        assertThat(uploadId).isNotBlank();
        assertThat(uploadId).doesNotStartWith("upload-");
        assertThat(created.path("parts")).hasSize(2);
        UUID mediaId = UUID.fromString(created.path("mediaId").asText());
        mockMvc.perform(post("/api/v1/media/" + mediaId + "/parts/2")
                        .with(csrf())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partNumber").value(2))
                .andExpect(jsonPath("$.url").isNotEmpty());

        HttpClient client = HttpClient.newHttpClient();
        java.util.List<String> etags = new java.util.ArrayList<>();
        byte[][] bodies = new byte[][] {new byte[(int) S3MultipartUrls.PART_SIZE_BYTES], new byte[] {7}};
        bodies[0][0] = 1;
        for (int i = 0; i < 2; i++) {
            JsonNode part = created.path("parts").get(i);
            assertThat(part.path("partNumber").asInt()).isEqualTo(i + 1);
            String url = part.path("url").asText();
            assertThat(url).contains("partNumber=" + (i + 1));
            assertThat(url).contains(uploadId);
            HttpResponse<String> put = client.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .PUT(HttpRequest.BodyPublishers.ofByteArray(bodies[i]))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(put.statusCode()).isBetween(200, 299);
            String etag = put.headers().firstValue("etag").orElse("");
            assertThat(etag).isNotBlank();
            etags.add(etag);
        }

        mockMvc.perform(post("/api/v1/media/" + mediaId + "/complete")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("uploadId", uploadId, "etags", etags))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processing"));
    }

    @Test
    void foldersAndQuotaDoNotBindAsMediaId() throws Exception {
        String token = registerVerifyLogin("media-quota-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);

        MediaAssetEntity asset = new MediaAssetEntity();
        asset.setId(ru.posts.common.uuid.UuidV7.generate());
        asset.setWorkspaceId(workspaceId);
        asset.setKind("image");
        asset.setMime("image/jpeg");
        asset.setFilename("foldered.jpg");
        asset.setFolder("Кампании");
        asset.setS3Key("workspaces/" + workspaceId + "/media/" + asset.getId() + "/foldered.jpg");
        asset.setSizeBytes(20L);
        asset.setStatus("ready");
        asset.setDerivatives(Map.of());
        asset.setTags(java.util.List.of());
        asset.setCreatedAt(Instant.now());
        asset.setUpdatedAt(Instant.now());
        mediaAssetRepository.save(asset);

        mockMvc.perform(get("/api/v1/media/folders")
                        .header("Authorization", bearer(token))
                        .param("workspaceId", workspaceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Кампании"));

        mockMvc.perform(get("/api/v1/media/quota")
                        .header("Authorization", bearer(token))
                        .param("workspaceId", workspaceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.usedBytes").isNumber())
                .andExpect(jsonPath("$.limitBytes").isNumber());

        mockMvc.perform(get("/api/v1/media/not-a-uuid").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void ssrfImportUrlBlocked() throws Exception {
        String token = registerVerifyLogin("media-ssrf-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        for (String url : new String[] {
            "http://127.0.0.1/secret",
            "http://169.254.169.254/latest/meta-data/",
            "file:///etc/passwd",
            "http://localhost/x"
        }) {
            mockMvc.perform(post("/api/v1/media/import-url")
                            .with(csrf())
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("workspaceId", workspaceId.toString(), "url", url))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation_failed"));
        }
    }

    @Test
    void idorForeignMediaReturns404() throws Exception {
        String tokenA = registerVerifyLogin("media-idor-a-" + UUID.randomUUID() + "@example.com", "A");
        String tokenB = registerVerifyLogin("media-idor-b-" + UUID.randomUUID() + "@example.com", "B");
        UUID workspaceA = firstWorkspaceId(tokenA);

        MediaAssetEntity asset = new MediaAssetEntity();
        asset.setId(UuidV7.generate());
        asset.setWorkspaceId(workspaceA);
        asset.setKind("image");
        asset.setMime("image/jpeg");
        asset.setFilename("secret.jpg");
        asset.setS3Key("workspaces/" + workspaceA + "/media/" + asset.getId() + "/secret.jpg");
        asset.setSizeBytes(10L);
        asset.setStatus("ready");
        asset.setDerivatives(Map.of());
        asset.setTags(java.util.List.of());
        asset.setCreatedAt(Instant.now());
        asset.setUpdatedAt(Instant.now());
        mediaAssetRepository.save(asset);

        mockMvc.perform(get("/api/v1/media/" + asset.getId()).header("Authorization", bearer(tokenB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/media/" + asset.getId()).with(csrf()).header("Authorization", bearer(tokenB)))
                .andExpect(status().isNotFound());
    }

    private static byte[] minimalJpegBytes() {
        // JFIF header + minimal payload recognized as image/jpeg by many parsers / S3
        return new byte[] {
            (byte) 0xFF,
            (byte) 0xD8,
            (byte) 0xFF,
            (byte) 0xE0,
            0x00,
            0x10,
            0x4A,
            0x46,
            0x49,
            0x46,
            0x00,
            0x01,
            0x01,
            0x00,
            0x00,
            0x01,
            0x00,
            0x01,
            0x00,
            0x00,
            (byte) 0xFF,
            (byte) 0xD9
        };
    }

    private UUID firstWorkspaceId(String accessToken) throws Exception {
        JsonNode list = readJson(mockMvc.perform(get("/api/v1/workspaces").header("Authorization", bearer(accessToken)))
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
