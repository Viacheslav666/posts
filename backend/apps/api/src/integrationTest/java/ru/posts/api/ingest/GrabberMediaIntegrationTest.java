package ru.posts.api.ingest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.posts.api.auth.mail.LoggingAuthMailSender;
import ru.posts.common.uuid.UuidV7;
import ru.posts.grabber.GrabberRunProcessor;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.GrabberItemEntity;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.PostVariantEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.GrabberItemRepository;
import ru.posts.persistence.repository.OutboxRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.PostVariantRepository;
import ru.posts.persistence.repository.PublicationRepository;
import ru.posts.persistence.repository.WorkspaceRepository;

/** Grabber media pipeline: attachments are carried into the target VK post (photo/video re-upload). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class GrabberMediaIntegrationTest {

    private static final Pattern TOKEN_IN_BODY = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void grabberProps(DynamicPropertyRegistry registry) {
        registry.add("posts.grabber.vk.base-url", wireMock::baseUrl);
        registry.add("posts.grabber.inline-runs", () -> "true");
        registry.add("posts.grabber.vk.access-token", () -> "test-token");
        registry.add("posts.grabber.ssrf.allowed-hosts", () -> "localhost,127.0.0.1");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LoggingAuthMailSender mailSender;

    @Autowired
    ChannelRepository channelRepository;

    @Autowired
    GrabberItemRepository itemRepository;

    @Autowired
    PublicationRepository publicationRepository;

    @Autowired
    PostVariantRepository postVariantRepository;

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    PlanRepository planRepository;

    @Autowired
    WorkspaceRepository workspaceRepository;

    @Autowired
    GrabberRunProcessor runProcessor;

    @BeforeEach
    void setUp() {
        mailSender.clear();
        wireMock.resetAll();
        publicationRepository.deleteAll();
        itemRepository.deleteAll();
        String base = wireMock.baseUrl();
        stubMethod("photos.getWallUploadServer", "{\"response\":{\"upload_url\":\"" + base + "/upload/photo\"}}");
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/upload/photo"))
                .willReturn(json("{\"server\":1,\"photo\":\"[{}]\",\"hash\":\"h\"}")));
        stubMethod("photos.saveWallPhoto", "{\"response\":[{\"id\":501,\"owner_id\":-999}]}");
        stubMethod(
                "video.save",
                "{\"response\":{\"upload_url\":\"" + base + "/upload/video\",\"video_id\":777,\"owner_id\":-999}}");
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/upload/video"))
                .willReturn(json("{\"size\":2048}")));
        stubMethod(
                "video.get",
                "{\"response\":{\"count\":1,\"items\":[{\"id\":5,\"owner_id\":-123,\"title\":\"Ролик\","
                        + "\"files\":{\"mp4_480\":\"" + base + "/cdn/v.mp4\"}}]}}");
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/cdn/p.jpg"))
                .willReturn(aResponse().withHeader("Content-Type", "image/jpeg").withBody(new byte[] {1, 2, 3})));
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/cdn/v.mp4"))
                .willReturn(aResponse().withHeader("Content-Type", "video/mp4").withBody(new byte[2048])));
    }

    @Test
    @DisplayName("Auto mode: photo/video re-uploaded, other attachments referenced, carousel kept")
    void autoPublishCarriesAttachments() throws Exception {
        stubWall("""
                {"id":5,"owner_id":-123,"title":"Ролик"}""");
        Fixture fx = bootstrap();
        UUID ruleId = createRule(fx, Map.of("reupload_video", true, "attachments_mode", "carousel"));

        runProcessor.process(ruleId, null, "manual", "trace-media");

        GrabberItemEntity item =
                itemRepository.findByRuleIdOrderByDetectedAtDesc(ruleId).getFirst();
        assertThat(item.getStatus()).isIn("scheduled", "published", "approved");
        PostVariantEntity variant =
                postVariantRepository.findByPostId(item.getPostId()).getFirst();
        assertThat((List<Object>) variant.getContent().get("stringAttachments"))
                .containsExactly(
                        "photo-999_501",
                        "video-999_777",
                        "audio-123_9",
                        "poll-123_10",
                        "market-123_11",
                        "https://example.com/news");
        assertThat(variant.getOptions()).containsEntry("primaryAttachmentsMode", "carousel");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/photos.saveWallPhoto"))
                .withRequestBody(containing("group_id=999")));
        wireMock.verify(
                postRequestedFor(urlPathEqualTo("/method/video.save")).withRequestBody(containing("group_id=999")));
    }

    @Test
    @DisplayName("Перезалить видео: private video → post not published, item failed, notification queued")
    void failedRequiredVideoReuploadBlocksPublication() throws Exception {
        stubWall("""
                {"id":5,"owner_id":-123,"title":"Ролик","is_private":1}""");
        Fixture fx = bootstrap();
        UUID ruleId = createRule(fx, Map.of("reupload_video", true));

        runProcessor.process(ruleId, null, "manual", "trace-media-fail");

        GrabberItemEntity item =
                itemRepository.findByRuleIdOrderByDetectedAtDesc(ruleId).getFirst();
        assertThat(item.getStatus()).isEqualTo("publish_failed");
        assertThat(item.getSkipReason()).isEqualTo("video_reupload_failed:video_private");
        assertThat(item.getPostId()).isNull();
        assertThat(publicationRepository.findAll()).isEmpty();
        assertThat(outboxRepository.findAll())
                .anyMatch(o -> "grabber.item_failed".equals(o.getEventType())
                        && item.getId().equals(o.getAggregateId()));
    }

    @Test
    @DisplayName("Strip rules remove photos/videos; user attachments used when nothing is left")
    void stripAndUserAttachments() throws Exception {
        stubWall("""
                {"id":5,"owner_id":-123,"title":"Ролик"}""");
        Fixture fx = bootstrap();
        UUID ruleId = createRule(
                fx,
                Map.of(
                        "strip_photo", true,
                        "strip_video", true,
                        "strip_music", true,
                        "strip_audio", true,
                        "strip_poll", true,
                        "strip_product", true,
                        "strip_market", true,
                        "strip_all_links", true,
                        "user_attachments", "[keyTextEntry]photo-1_42|смеют[/keyTextEntry]"));

        runProcessor.process(ruleId, null, "manual", "trace-media-strip");

        GrabberItemEntity item =
                itemRepository.findByRuleIdOrderByDetectedAtDesc(ruleId).getFirst();
        PostVariantEntity variant =
                postVariantRepository.findByPostId(item.getPostId()).getFirst();
        assertThat((List<Object>) variant.getContent().get("stringAttachments")).containsExactly("photo-1_42");
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/method/photos.saveWallPhoto")));
    }

    @Test
    @DisplayName("Invalid user attachments → 422 on save")
    void invalidUserAttachmentsRejected() throws Exception {
        Fixture fx = bootstrap();
        mockMvc.perform(post("/api/v1/workspaces/" + fx.workspaceId + "/projects/" + fx.projectId + "/ingest/rules")
                        .with(csrf())
                        .header("Authorization", "Bearer " + fx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type",
                                "grabber",
                                "name",
                                "Bad attachments",
                                "targetChannelId",
                                fx.channelId.toString(),
                                "userResponsibilityAccepted",
                                true,
                                "config",
                                Map.of(
                                        "sourceIds", List.of("-123"),
                                        "scanIntervalMinutes", 15,
                                        "transform", Map.of("user_attachments", "[rand]photo-1_2[/rand]"))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    private void stubWall(String videoPayload) {
        String base = wireMock.baseUrl();
        stubMethod(
                "wall.get",
                """
                {"response":{"count":1,"items":[{"id":10,"owner_id":-123,"from_id":-123,
                  "text":"Все смеются над новостью","marked_as_ads":0,"attachments":[
                    {"type":"photo","photo":{"id":1,"owner_id":-123,"sizes":[{"type":"x","url":"%s/cdn/p.jpg","width":800,"height":600}]}},
                    {"type":"video","video":%s},
                    {"type":"audio","audio":{"id":9,"owner_id":-123}},
                    {"type":"poll","poll":{"id":10,"owner_id":-123}},
                    {"type":"market","market":{"id":11,"owner_id":-123}},
                    {"type":"link","link":{"url":"https://example.com/news","title":"News"}}
                  ]}]}}
                """
                        .formatted(base, videoPayload));
    }

    private void stubMethod(String method, String body) {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/" + method))
                .willReturn(json(body)));
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    private UUID createRule(Fixture fx, Map<String, Object> transform) throws Exception {
        JsonNode rule = readJson(mockMvc.perform(
                        post("/api/v1/workspaces/" + fx.workspaceId + "/projects/" + fx.projectId + "/ingest/rules")
                                .with(csrf())
                                .header("Authorization", "Bearer " + fx.token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "type",
                                        "grabber",
                                        "name",
                                        "Media",
                                        "targetChannelId",
                                        fx.channelId.toString(),
                                        "userResponsibilityAccepted",
                                        true,
                                        "activate",
                                        true,
                                        "config",
                                        Map.of(
                                                "sourceIds",
                                                List.of("-123"),
                                                "scanIntervalMinutes",
                                                15,
                                                "publishMode",
                                                "auto",
                                                "transform",
                                                transform)))))
                .andExpect(status().isCreated())
                .andReturn());
        return UUID.fromString(rule.get("id").asText());
    }

    private Fixture bootstrap() throws Exception {
        String email = "media-" + UUID.randomUUID() + "@example.com";
        String token = registerVerifyLogin(email);
        JsonNode list = readJson(mockMvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
        UUID workspaceId = UUID.fromString(list.get("items").get(0).get("id").asText());
        PlanEntity agency = planRepository.findByCode("agency").orElseThrow();
        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        workspace.setPlanId(agency.getId());
        workspaceRepository.save(workspace);
        JsonNode project = readJson(mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "P1", "color", "#112233"))))
                .andExpect(status().isCreated())
                .andReturn());
        UUID projectId = UUID.fromString(project.get("id").asText());
        ChannelEntity channel = new ChannelEntity();
        channel.setId(UuidV7.generate());
        channel.setWorkspaceId(workspaceId);
        channel.setProjectId(projectId);
        channel.setNetwork("vk");
        channel.setExternalId("-999");
        channel.setType("group");
        channel.setTitle("Target VK");
        channel.setStatus("active");
        channel.setTimezone("Europe/Moscow");
        channel.setCreatedAt(Instant.now());
        channel.setUpdatedAt(Instant.now());
        channelRepository.save(channel);
        return new Fixture(token, workspaceId, projectId, channel.getId());
    }

    private String registerVerifyLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "Password1!Password1!", "name", "Media Owner"))))
                .andExpect(status().isCreated());
        String verifyToken = mailSender.messages().stream()
                .filter(m -> m.to().equalsIgnoreCase(email))
                .reduce((a, b) -> b)
                .map(m -> {
                    Matcher matcher = TOKEN_IN_BODY.matcher(m.body());
                    assertThat(matcher.find()).isTrue();
                    return matcher.group(1);
                })
                .orElseThrow();
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

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private record Fixture(String token, UUID workspaceId, UUID projectId, UUID channelId) {}
}
