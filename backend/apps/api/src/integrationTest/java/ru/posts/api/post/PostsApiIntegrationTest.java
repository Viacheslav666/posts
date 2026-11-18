package ru.posts.api.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.posts.api.auth.mail.LoggingAuthMailSender;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.PublicationRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class PostsApiIntegrationTest {

    private static final Pattern TOKEN_IN_BODY = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LoggingAuthMailSender mailSender;

    @Autowired
    ChannelRepository channelRepository;

    @Autowired
    PublicationRepository publicationRepository;

    @BeforeEach
    void setUp() {
        mailSender.clear();
    }

    @Test
    @DisplayName("create draft + schedule materializes publications; calendar filters")
    void createScheduleAndCalendar() throws Exception {
        String token = registerVerifyLogin("posts-a-" + UUID.randomUUID() + "@example.com", "Owner A");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");

        Instant scheduledAt = Instant.parse("2026-11-25T12:00:00Z");
        JsonNode created = readJson(mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "title", "Hello calendar",
                                "baseContent", Map.of("text", "Привет Posts", "mediaIds", List.of()),
                                "scheduledAt", scheduledAt.toString(),
                                "timezone", "Europe/Moscow",
                                "ad", false,
                                "variants",
                                        List.of(Map.of(
                                                "channelId",
                                                channel.getId().toString(),
                                                "network",
                                                "vk",
                                                "content",
                                                Map.of("text", "Привет VK")))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("scheduled"))
                .andExpect(jsonPath("$.contentKind").value("post"))
                .andExpect(jsonPath("$.sourceType").value("manual"))
                .andExpect(jsonPath("$.variants[0].channelId")
                        .value(channel.getId().toString()))
                .andReturn());

        UUID postId = UUID.fromString(created.get("id").asText());
        List<PublicationEntity> pubs = publicationRepository.findByPostId(postId);
        assertThat(pubs).isNotEmpty();
        assertThat(pubs.getFirst().getState()).isEqualTo("pending");
        assertThat(pubs.getFirst().getIdempotencyKey()).isNotBlank();

        mockMvc.perform(get("/api/v1/calendar")
                        .header("Authorization", bearer(token))
                        .param("workspaceId", workspaceId.toString())
                        .param("from", scheduledAt.minus(1, ChronoUnit.HOURS).toString())
                        .param("to", scheduledAt.plus(1, ChronoUnit.DAYS).toString())
                        .param("timezone", "Europe/Moscow")
                        .param("channelId", channel.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(postId.toString()))
                .andExpect(jsonPath("$[0].contentKind").value("post"));

        mockMvc.perform(get("/api/v1/posts")
                        .header("Authorization", bearer(token))
                        .param("workspaceId", workspaceId.toString())
                        .param("authorId", created.get("authorId").asText())
                        .param("sort", "scheduledAt_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(postId.toString()));

        mockMvc.perform(post("/api/v1/posts/preview")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("network", "tg", "content", Map.of("text", "<b>bold</b>")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.html").value("<b>bold</b>"));
    }

    @Test
    @DisplayName("Idempotency-Key replays same create; different body → 409")
    void createIdempotency() throws Exception {
        String token = registerVerifyLogin("posts-id-" + UUID.randomUUID() + "@example.com", "Owner Id");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "tg");
        String key = "post-create-" + UUID.randomUUID();

        Map<String, Object> body = Map.of(
                "workspaceId",
                workspaceId.toString(),
                "title",
                "Idempotent",
                "baseContent",
                Map.of("text", "once"),
                "ad",
                false,
                "variants",
                List.of(Map.of(
                        "channelId", channel.getId().toString(), "network", "tg", "content", Map.of("text", "once"))));

        JsonNode first = readJson(mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn());

        JsonNode second = readJson(mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(second.get("id").asText()).isEqualTo(first.get("id").asText());

        Map<String, Object> other = Map.of(
                "workspaceId",
                workspaceId.toString(),
                "title",
                "Different",
                "baseContent",
                Map.of("text", "twice"),
                "ad",
                false,
                "variants",
                List.of());
        mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(other)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("idempotency_conflict"));
    }

    @Test
    @DisplayName("Tenant isolation: foreign post/calendar → 404")
    void tenantIsolation() throws Exception {
        String tokenA = registerVerifyLogin("posts-ta-" + UUID.randomUUID() + "@example.com", "Owner A");
        String tokenB = registerVerifyLogin("posts-tb-" + UUID.randomUUID() + "@example.com", "Owner B");
        UUID wsA = firstWorkspaceId(tokenA);
        UUID wsB = firstWorkspaceId(tokenB);
        ChannelEntity channelA = seedChannel(wsA, "ok");

        JsonNode created = readJson(mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                wsA.toString(),
                                "title",
                                "Secret",
                                "baseContent",
                                Map.of("text", "private"),
                                "scheduledAt",
                                "2026-12-01T09:00:00Z",
                                "variants",
                                List.of(Map.of(
                                        "channelId",
                                        channelA.getId().toString(),
                                        "network",
                                        "ok",
                                        "content",
                                        Map.of("text", "private")))))))
                .andExpect(status().isCreated())
                .andReturn());
        UUID postId = UUID.fromString(created.get("id").asText());

        mockMvc.perform(get("/api/v1/posts/" + postId).header("Authorization", bearer(tokenB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/calendar")
                        .header("Authorization", bearer(tokenB))
                        .param("workspaceId", wsA.toString())
                        .param("from", "2026-12-01T00:00:00Z")
                        .param("to", "2026-12-02T00:00:00Z"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/posts")
                        .header("Authorization", bearer(tokenB))
                        .param("workspaceId", wsB.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    private ChannelEntity seedChannel(UUID workspaceId, String network) {
        ChannelEntity channel = new ChannelEntity();
        channel.setId(UuidV7.generate());
        channel.setWorkspaceId(workspaceId);
        channel.setNetwork(network);
        channel.setExternalId("ext-" + UUID.randomUUID());
        channel.setType("group");
        channel.setTitle("Test " + network);
        channel.setStatus("active");
        channel.setTimezone("Europe/Moscow");
        channel.setCreatedAt(Instant.now());
        channel.setUpdatedAt(Instant.now());
        return channelRepository.save(channel);
    }

    private String registerVerifyLogin(String email, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Password1!Password1!",
                                "name", name))))
                .andExpect(status().isCreated());
        String verifyToken = mailSender.messages().stream()
                .filter(m -> m.to().equalsIgnoreCase(email))
                .reduce((a, b) -> b)
                .map(m -> {
                    Matcher matcher = TOKEN_IN_BODY.matcher(m.body());
                    assertThat(matcher.find()).isTrue();
                    return matcher.group(1);
                })
                .orElseThrow(() -> new AssertionError("No mail for " + email));
        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", verifyToken))))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "Password1!Password1!"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper
                .readTree(login.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    private UUID firstWorkspaceId(String token) throws Exception {
        JsonNode list = readJson(mockMvc.perform(get("/api/v1/workspaces").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn());
        return UUID.fromString(list.get("items").get(0).get("id").asText());
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
