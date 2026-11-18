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
import java.util.ArrayList;
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
import ru.posts.persistence.entity.MediaAssetEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.MediaAssetRepository;
import ru.posts.persistence.repository.PublicationRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class Be29PostsIntegrationTest {

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
    MediaAssetRepository mediaAssetRepository;

    @Autowired
    PublicationRepository publicationRepository;

    @BeforeEach
    void setUp() {
        mailSender.clear();
    }

    @Test
    @DisplayName("BE-29: preview expands variables/spintax with timezone + expandedText")
    void previewExpandsVariables() throws Exception {
        String token = registerVerifyLogin("be29-prev-" + UUID.randomUUID() + "@example.com", "Owner");
        mockMvc.perform(post("/api/v1/posts/preview")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "network",
                                "vk",
                                "content",
                                Map.of("text", "Группа {group_name} ({group_id}) — {date} {time} [rand]a|b|c[/rand]"),
                                "timezone",
                                "Europe/Moscow",
                                "at",
                                "2026-11-18T10:30:00Z",
                                "groupId",
                                "-42",
                                "groupName",
                                "Тест",
                                "spintaxSeed",
                                "stable-seed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.expandedText").value(org.hamcrest.Matchers.containsString("Тест")))
                .andExpect(jsonPath("$.expandedText").value(org.hamcrest.Matchers.containsString("18.11.2026")))
                .andExpect(jsonPath("$.expandedText").value(org.hamcrest.Matchers.containsString("13:30")))
                .andExpect(jsonPath("$.expandedText")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("[rand]"))));
    }

    @Test
    @DisplayName("BE-29: invalid string attachment → 422 vk_string_attachment_invalid")
    void invalidStringAttachmentRejected() throws Exception {
        String token = registerVerifyLogin("be29-att-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");

        mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "title",
                                "bad att",
                                "baseContent",
                                Map.of("text", "x", "stringAttachments", List.of("not-a-vk-attachment")),
                                "variants",
                                List.of(Map.of(
                                        "channelId",
                                        channel.getId().toString(),
                                        "network",
                                        "vk",
                                        "content",
                                        Map.of("text", "x")))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("vk_string_attachment_invalid"));
    }

    @Test
    @DisplayName("BE-29: recurrence.times[] materializes multiple publications per day")
    void recurrenceTimesMaterializes() throws Exception {
        String token = registerVerifyLogin("be29-times-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");

        Instant scheduledAt = Instant.parse("2026-11-20T00:00:00Z");
        JsonNode created = readJson(mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "title",
                                "multi time",
                                "baseContent",
                                Map.of("text", "hello"),
                                "scheduledAt",
                                scheduledAt.toString(),
                                "timezone",
                                "UTC",
                                "recurrence",
                                Map.of(
                                        "freq",
                                        "daily",
                                        "interval",
                                        1,
                                        "until",
                                        Instant.parse("2026-11-20T23:59:59Z").toString(),
                                        "times",
                                        List.of("09:00", "18:00")),
                                "variants",
                                List.of(Map.of(
                                        "channelId",
                                        channel.getId().toString(),
                                        "network",
                                        "vk",
                                        "content",
                                        Map.of("text", "hello")))))))
                .andExpect(status().isCreated())
                .andReturn());

        UUID postId = UUID.fromString(created.get("id").asText());
        List<PublicationEntity> pubs = publicationRepository.findByPostId(postId);
        assertThat(pubs).hasSize(2);
        assertThat(pubs.stream().map(PublicationEntity::getRunAt).sorted().toList())
                .containsExactly(Instant.parse("2026-11-20T09:00:00Z"), Instant.parse("2026-11-20T18:00:00Z"));
    }

    @Test
    @DisplayName("BE-29 / P16: batch posts from media")
    void batchPostsFromMedia() throws Exception {
        String token = registerVerifyLogin("be29-batch-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");
        List<String> mediaIds = List.of(
                seedMedia(workspaceId).toString(), seedMedia(workspaceId).toString());

        mockMvc.perform(post("/api/v1/posts/batch")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "channelIds",
                                List.of(channel.getId().toString()),
                                "mediaIds",
                                mediaIds,
                                "scheduledAt",
                                Instant.now().plus(1, ChronoUnit.HOURS).toString(),
                                "stepMinutes",
                                5,
                                "text",
                                "Batch {group_name}",
                                "timezone",
                                "Europe/Moscow",
                                "ad",
                                false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.postIds.length()").value(2));
    }

    @Test
    @DisplayName("BE-29: batch > 50 → 422 post_batch_limit")
    void batchPostsOverLimit() throws Exception {
        String token = registerVerifyLogin("be29-batch-lim-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");
        List<String> mediaIds = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            mediaIds.add(UuidV7.generate().toString());
        }
        mockMvc.perform(post("/api/v1/posts/batch")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "channelIds",
                                List.of(channel.getId().toString()),
                                "mediaIds",
                                mediaIds,
                                "scheduledAt",
                                Instant.now().plus(1, ChronoUnit.HOURS).toString(),
                                "ad",
                                false))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("post_batch_limit"));
    }

    private UUID seedMedia(UUID workspaceId) {
        MediaAssetEntity asset = new MediaAssetEntity();
        asset.setId(UuidV7.generate());
        asset.setWorkspaceId(workspaceId);
        asset.setKind("image");
        asset.setMime("image/jpeg");
        asset.setSizeBytes(2048L);
        asset.setFilename("batch.jpg");
        asset.setS3Key("workspaces/" + workspaceId + "/media/" + asset.getId() + "/batch.jpg");
        asset.setStatus("ready");
        asset.setTags(List.of());
        asset.setDerivatives(Map.of());
        asset.setCreatedAt(Instant.now());
        asset.setUpdatedAt(Instant.now());
        return mediaAssetRepository.save(asset).getId();
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
