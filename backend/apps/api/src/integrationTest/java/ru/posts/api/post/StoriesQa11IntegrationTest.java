package ru.posts.api.post;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import ru.posts.api.ord.OrdWireMockStubs;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.MediaAssetEntity;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.MediaAssetRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.PublicationRepository;
import ru.posts.persistence.repository.WorkspaceRepository;

/**
 * QA-11 / TC-STORY P0 automation for BE-16 scheduled stories (WireMock ORD + Testcontainers).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class StoriesQa11IntegrationTest {

    private static final Pattern TOKEN_IN_BODY = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final long OVERSIZE_VIDEO_BYTES = 350L * 1024L * 1024L + 1L;

    @RegisterExtension
    static WireMockExtension vkOrd = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension yandexOrd = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @DynamicPropertySource
    static void ordUrls(DynamicPropertyRegistry registry) {
        registry.add("posts.ord.vk.base-url", vkOrd::baseUrl);
        registry.add("posts.ord.yandex.base-url", yandexOrd::baseUrl);
        registry.add("posts.ord.vk.read-timeout", () -> "1s");
        registry.add("posts.ord.yandex.read-timeout", () -> "1s");
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
    MediaAssetRepository mediaAssetRepository;

    @Autowired
    PublicationRepository publicationRepository;

    @Autowired
    PlanRepository planRepository;

    @Autowired
    WorkspaceRepository workspaceRepository;

    @BeforeEach
    void setUp() {
        mailSender.clear();
        vkOrd.resetAll();
        yandexOrd.resetAll();
        OrdWireMockStubs.stubVkHappyPath(vkOrd);
        OrdWireMockStubs.stubYandexHappyPath(yandexOrd);
    }

    @Test
    @DisplayName("TC-STORY-04: VK internal link_url accepted on create/schedule")
    void tcStory04VkInternalLinkAccepted() throws Exception {
        String token = registerVerifyLogin("story-04-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk", "ready");
        UUID mediaId = seedMedia(workspaceId);

        Instant scheduledAt = Instant.now().plus(2, ChronoUnit.HOURS);
        mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "title",
                                "VK Story link",
                                "contentKind",
                                "story",
                                "baseContent",
                                Map.of("text", "", "mediaIds", List.of(mediaId.toString())),
                                "scheduledAt",
                                scheduledAt.toString(),
                                "timezone",
                                "Europe/Moscow",
                                "ad",
                                false,
                                "story",
                                Map.of("ttl", "ttl_24h", "linkText", "go_to", "linkUrl", "https://vk.com/club1"),
                                "variants",
                                List.of(Map.of(
                                        "channelId",
                                        channel.getId().toString(),
                                        "network",
                                        "vk",
                                        "content",
                                        Map.of("text", "", "mediaIds", List.of(mediaId.toString()))))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentKind").value("story"))
                .andExpect(jsonPath("$.status").value("scheduled"))
                .andExpect(jsonPath("$.story.linkText").value("go_to"))
                .andExpect(jsonPath("$.story.linkUrl").value("https://vk.com/club1"));
    }

    @Test
    @DisplayName("TC-STORY-08: everyday recurrence + pause/resume")
    void tcStory08EverydayPauseResume() throws Exception {
        String token = registerVerifyLogin("story-08-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk", "ready");
        UUID mediaId = seedMedia(workspaceId);

        Instant scheduledAt = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        JsonNode created = readJson(mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "title",
                                "Everyday story",
                                "contentKind",
                                "story",
                                "baseContent",
                                Map.of("text", "", "mediaIds", List.of(mediaId.toString())),
                                "scheduledAt",
                                scheduledAt.toString(),
                                "timezone",
                                "UTC",
                                "recurrence",
                                Map.of("freq", "daily", "interval", 1),
                                "ad",
                                false,
                                "story",
                                Map.of("ttl", "ttl_24h"),
                                "variants",
                                List.of(Map.of(
                                        "channelId",
                                        channel.getId().toString(),
                                        "network",
                                        "vk",
                                        "content",
                                        Map.of("mediaIds", List.of(mediaId.toString()))))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("scheduled"))
                .andReturn());

        UUID postId = UUID.fromString(created.path("id").asText());
        int version = created.path("version").asInt();
        List<PublicationEntity> beforePause = publicationRepository.findByPostId(postId);
        assertThat(beforePause).hasSizeGreaterThanOrEqualTo(2);
        assertThat(beforePause).allMatch(p -> "pending".equals(p.getState()));

        PublicationEntity published = beforePause.getFirst();
        published.setState("succeeded");
        publicationRepository.saveAndFlush(published);

        JsonNode paused = readJson(mockMvc.perform(post("/api/v1/posts/" + postId + "/pause")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("version", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("paused"))
                .andReturn());

        List<PublicationEntity> afterPause = publicationRepository.findByPostId(postId);
        assertThat(afterPause.stream().filter(p -> "pending".equals(p.getState())))
                .isEmpty();
        assertThat(afterPause.stream().anyMatch(p -> "succeeded".equals(p.getState())))
                .isTrue();

        // Prefer version from pause response; fall back to GET if serializer lagged @Version.
        int resumeVersion = paused.path("version").asInt();
        if (resumeVersion <= version) {
            JsonNode current =
                    readJson(mockMvc.perform(get("/api/v1/posts/" + postId).header("Authorization", bearer(token)))
                            .andExpect(status().isOk())
                            .andReturn());
            resumeVersion = current.path("version").asInt();
        }
        MvcResult resumeResult = mockMvc.perform(post("/api/v1/posts/" + postId + "/resume")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("version", resumeVersion))))
                .andReturn();
        assertThat(resumeResult.getResponse().getStatus())
                .as("resume status; body=%s", resumeResult.getResponse().getContentAsString())
                .isEqualTo(200);
        assertThat(objectMapper
                        .readTree(resumeResult.getResponse().getContentAsString())
                        .path("status")
                        .asText())
                .isEqualTo("scheduled");

        List<PublicationEntity> afterResume = publicationRepository.findByPostId(postId);
        assertThat(afterResume.stream().anyMatch(p -> "succeeded".equals(p.getState())))
                .isTrue();
        assertThat(afterResume.stream().anyMatch(p -> "pending".equals(p.getState())))
                .isTrue();
    }

    @Test
    @DisplayName("TC-STORY-11: story ad marking VK registers erid")
    void tcStory11VkStoryAdRegistersErid() throws Exception {
        String token = registerVerifyLogin("story-11-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk", "ready");
        UUID mediaId = seedMedia(workspaceId);
        upsertConnection(token, workspaceId, "vk_ord", "vk-test-key");

        JsonNode draft = readJson(mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "title",
                                "Story ad",
                                "contentKind",
                                "story",
                                "baseContent",
                                Map.of("text", "", "mediaIds", List.of(mediaId.toString())),
                                "ad",
                                true,
                                "adMarking",
                                validAdMarking(),
                                "story",
                                Map.of("ttl", "ttl_24h"),
                                "variants",
                                List.of(Map.of(
                                        "channelId",
                                        channel.getId().toString(),
                                        "network",
                                        "vk",
                                        "content",
                                        Map.of("mediaIds", List.of(mediaId.toString()))))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("draft"))
                .andReturn());

        UUID postId = UUID.fromString(draft.path("id").asText());
        Instant scheduledAt = Instant.now().plus(2, ChronoUnit.HOURS);
        mockMvc.perform(post("/api/v1/posts/" + postId + "/schedule")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduledAt",
                                scheduledAt.toString(),
                                "version",
                                draft.path("version").asInt()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("scheduled"))
                .andExpect(jsonPath("$.adMarking.erid").value(OrdWireMockStubs.VK_ERID));
    }

    @Test
    @DisplayName("TC-STORY-12: OK story ads allowed; Max story → story_unsupported_network")
    void tcStory12OkAdsAllowedMaxRejected() throws Exception {
        String token = registerVerifyLogin("story-12-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity okChannel = seedChannel(workspaceId, "ok", "missing");
        ChannelEntity maxChannel = seedChannel(workspaceId, "max", "missing");
        UUID mediaId = seedMedia(workspaceId);
        upsertConnection(token, workspaceId, "vk_ord", "vk-test-key");

        Instant scheduledAt = Instant.now().plus(2, ChronoUnit.HOURS);
        mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "title",
                                "OK Story ad",
                                "contentKind",
                                "story",
                                "baseContent",
                                Map.of("text", "", "mediaIds", List.of(mediaId.toString())),
                                "scheduledAt",
                                scheduledAt.toString(),
                                "ad",
                                true,
                                "adMarking",
                                validAdMarking(),
                                "story",
                                Map.of("ttl", "ttl_24h"),
                                "variants",
                                List.of(Map.of(
                                        "channelId",
                                        okChannel.getId().toString(),
                                        "network",
                                        "ok",
                                        "content",
                                        Map.of("mediaIds", List.of(mediaId.toString()))))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentKind").value("story"))
                .andExpect(jsonPath("$.status").value("scheduled"))
                .andExpect(jsonPath("$.adMarking.erid").value(OrdWireMockStubs.VK_ERID));

        mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "contentKind",
                                "story",
                                "baseContent",
                                Map.of("text", "", "mediaIds", List.of(mediaId.toString())),
                                "scheduledAt",
                                scheduledAt.toString(),
                                "ad",
                                true,
                                "adMarking",
                                validAdMarking(),
                                "story",
                                Map.of("ttl", "ttl_24h"),
                                "variants",
                                List.of(Map.of(
                                        "channelId",
                                        maxChannel.getId().toString(),
                                        "network",
                                        "max",
                                        "content",
                                        Map.of("mediaIds", List.of(mediaId.toString()))))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("story_unsupported_network"));
    }

    @Test
    @DisplayName("TC-STORY-14: Author draft ok, schedule 403")
    void tcStory14AuthorCannotSchedule() throws Exception {
        String ownerToken = registerVerifyLogin("story-14-own-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(ownerToken);
        upgradeToAgency(workspaceId);
        ChannelEntity channel = seedChannel(workspaceId, "vk", "ready");
        UUID mediaId = seedMedia(workspaceId);

        JsonNode project = readJson(mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .with(csrf())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Stories", "color", "#112233"))))
                .andExpect(status().isCreated())
                .andReturn());
        UUID projectId = UUID.fromString(project.path("id").asText());

        String authorEmail = "story-14-author-" + UUID.randomUUID() + "@example.com";
        registerVerifyLogin(authorEmail, "Author");
        inviteAndAccept(ownerToken, workspaceId, authorEmail, "author", List.of(projectId));
        String authorToken = login(authorEmail);

        JsonNode draft = readJson(mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(authorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "projectId",
                                projectId.toString(),
                                "contentKind",
                                "story",
                                "baseContent",
                                Map.of("text", "", "mediaIds", List.of(mediaId.toString())),
                                "ad",
                                false,
                                "story",
                                Map.of("ttl", "ttl_24h"),
                                "variants",
                                List.of(Map.of(
                                        "channelId",
                                        channel.getId().toString(),
                                        "network",
                                        "vk",
                                        "content",
                                        Map.of("mediaIds", List.of(mediaId.toString()))))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("draft"))
                .andReturn());

        UUID postId = UUID.fromString(draft.path("id").asText());
        mockMvc.perform(post("/api/v1/posts/" + postId + "/schedule")
                        .with(csrf())
                        .header("Authorization", bearer(authorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduledAt",
                                Instant.now().plus(1, ChronoUnit.HOURS).toString(),
                                "version",
                                draft.path("version").asInt()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    @DisplayName("TC-STORY-17: invalid media (.bmp) → 422 story_media_invalid")
    void tcStory17InvalidBmpMedia() throws Exception {
        String token = registerVerifyLogin("story-17-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk", "ready");
        UUID bmpId = seedMedia(workspaceId, "image/bmp", 4096L, "story.bmp");
        UUID octetId = seedMedia(workspaceId, "application/octet-stream", 4096L, "story.bin");

        Instant scheduledAt = Instant.now().plus(1, ChronoUnit.HOURS);
        mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "contentKind",
                                "story",
                                "baseContent",
                                Map.of("text", "", "mediaIds", List.of(bmpId.toString())),
                                "scheduledAt",
                                scheduledAt.toString(),
                                "ad",
                                false,
                                "story",
                                Map.of("ttl", "ttl_24h"),
                                "variants",
                                List.of(Map.of(
                                        "channelId",
                                        channel.getId().toString(),
                                        "network",
                                        "vk",
                                        "content",
                                        Map.of("mediaIds", List.of(bmpId.toString()))))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("story_media_invalid"));

        mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "contentKind",
                                "story",
                                "baseContent",
                                Map.of("text", "", "mediaIds", List.of(octetId.toString())),
                                "scheduledAt",
                                scheduledAt.toString(),
                                "ad",
                                false,
                                "story",
                                Map.of("ttl", "ttl_24h"),
                                "variants",
                                List.of(Map.of(
                                        "channelId",
                                        channel.getId().toString(),
                                        "network",
                                        "vk",
                                        "content",
                                        Map.of("mediaIds", List.of(octetId.toString()))))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("story_media_invalid"));
    }

    @Test
    @DisplayName("TC-STORY-19: IDOR other workspace → 404 on GET post")
    void tcStory19IdorOtherWorkspace() throws Exception {
        String ownerA = registerVerifyLogin("story-19a-" + UUID.randomUUID() + "@example.com", "Owner A");
        UUID wsA = firstWorkspaceId(ownerA);
        ChannelEntity channel = seedChannel(wsA, "vk", "ready");
        UUID mediaId = seedMedia(wsA);

        JsonNode created = readJson(mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                wsA.toString(),
                                "contentKind",
                                "story",
                                "baseContent",
                                Map.of("text", "", "mediaIds", List.of(mediaId.toString())),
                                "ad",
                                false,
                                "story",
                                Map.of("ttl", "ttl_24h"),
                                "variants",
                                List.of(Map.of(
                                        "channelId",
                                        channel.getId().toString(),
                                        "network",
                                        "vk",
                                        "content",
                                        Map.of("mediaIds", List.of(mediaId.toString()))))))))
                .andExpect(status().isCreated())
                .andReturn());
        UUID postId = UUID.fromString(created.path("id").asText());

        String ownerB = registerVerifyLogin("story-19b-" + UUID.randomUUID() + "@example.com", "Owner B");
        mockMvc.perform(get("/api/v1/posts/" + postId).header("Authorization", bearer(ownerB)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-STORY-23: recurrence weekdays materializes Mon-Fri only")
    void tcStory23WeekdaysMaterialize() throws Exception {
        String token = registerVerifyLogin("story-23-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk", "ready");
        UUID mediaId = seedMedia(workspaceId);

        JsonNode draft = readJson(mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "contentKind",
                                "story",
                                "baseContent",
                                Map.of("text", "", "mediaIds", List.of(mediaId.toString())),
                                "ad",
                                false,
                                "story",
                                Map.of("ttl", "ttl_24h"),
                                "variants",
                                List.of(Map.of(
                                        "channelId",
                                        channel.getId().toString(),
                                        "network",
                                        "vk",
                                        "content",
                                        Map.of("mediaIds", List.of(mediaId.toString()))))))))
                .andExpect(status().isCreated())
                .andReturn());

        UUID postId = UUID.fromString(draft.path("id").asText());
        Instant scheduledAt = Instant.now().plusSeconds(3_600);
        mockMvc.perform(post("/api/v1/posts/" + postId + "/schedule")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduledAt",
                                scheduledAt.toString(),
                                "timezone",
                                "UTC",
                                "recurrence",
                                Map.of("freq", "weekdays", "interval", 1),
                                "version",
                                draft.path("version").asInt()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("scheduled"));

        List<PublicationEntity> pubs = publicationRepository.findByPostId(postId);
        assertThat(pubs).hasSizeGreaterThanOrEqualTo(5);
        Set<DayOfWeek> weekdays = EnumSet.of(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        for (PublicationEntity pub : pubs) {
            DayOfWeek dow = pub.getRunAt().atZone(ZoneOffset.UTC).getDayOfWeek();
            assertThat(weekdays).contains(dow);
            assertThat(dow).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        }
    }

    @Test
    @DisplayName("TC-STORY-24: ORD 5xx fail-closed on schedule story ads")
    void tcStory24Ord5xxFailClosed() throws Exception {
        OrdWireMockStubs.stubVkCreativeHttpStatus(vkOrd, 503);

        String token = registerVerifyLogin("story-24-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk", "ready");
        UUID mediaId = seedMedia(workspaceId);
        upsertConnection(token, workspaceId, "vk_ord", "vk-test-key");

        JsonNode draft = readJson(mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "contentKind",
                                "story",
                                "baseContent",
                                Map.of("text", "", "mediaIds", List.of(mediaId.toString())),
                                "ad",
                                true,
                                "adMarking",
                                validAdMarking(),
                                "story",
                                Map.of("ttl", "ttl_24h"),
                                "variants",
                                List.of(Map.of(
                                        "channelId",
                                        channel.getId().toString(),
                                        "network",
                                        "vk",
                                        "content",
                                        Map.of("mediaIds", List.of(mediaId.toString()))))))))
                .andExpect(status().isCreated())
                .andReturn());

        UUID postId = UUID.fromString(draft.path("id").asText());
        mockMvc.perform(post("/api/v1/posts/" + postId + "/schedule")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduledAt",
                                Instant.now().plus(2, ChronoUnit.HOURS).toString(),
                                "version",
                                draft.path("version").asInt()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ord_provider_unavailable"));

        assertThat(publicationRepository.findByPostId(postId)).isEmpty();
    }

    @Test
    @DisplayName("TC-STORY-30: video media sizeBytes > 350MB → 422 story_media_invalid")
    void tcStory30OversizedVideoRejected() throws Exception {
        String token = registerVerifyLogin("story-30-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk", "ready");
        UUID mediaId = seedMedia(workspaceId, "video/mp4", OVERSIZE_VIDEO_BYTES, "big-story.mp4");

        mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "contentKind",
                                "story",
                                "baseContent",
                                Map.of("text", "", "mediaIds", List.of(mediaId.toString())),
                                "scheduledAt",
                                Instant.now().plus(1, ChronoUnit.HOURS).toString(),
                                "ad",
                                false,
                                "story",
                                Map.of("ttl", "ttl_24h"),
                                "variants",
                                List.of(Map.of(
                                        "channelId",
                                        channel.getId().toString(),
                                        "network",
                                        "vk",
                                        "content",
                                        Map.of("mediaIds", List.of(mediaId.toString()))))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("story_media_invalid"));
    }

    @Test
    @DisplayName("TC-STORY-10: batch 3 stories via POST /api/v1/stories/batch")
    void tcStory10BatchThreeStories() throws Exception {
        String token = registerVerifyLogin("story-10-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk", "ready");
        List<String> mediaIds = List.of(
                seedMedia(workspaceId).toString(),
                seedMedia(workspaceId).toString(),
                seedMedia(workspaceId).toString());

        mockMvc.perform(post("/api/v1/stories/batch")
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
                                "timezone",
                                "Europe/Moscow",
                                "ad",
                                false,
                                "story",
                                Map.of("ttl", "ttl_24h")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.postIds.length()").value(3));
    }

    @Test
    @DisplayName("TC-STORY-25: batch > 50 → 422 story_batch_limit")
    void tcStory25BatchOverLimit() throws Exception {
        String token = registerVerifyLogin("story-25-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk", "ready");

        List<String> mediaIds = new ArrayList<>(51);
        for (int i = 0; i < 51; i++) {
            mediaIds.add(UuidV7.generate().toString());
        }

        mockMvc.perform(post("/api/v1/stories/batch")
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
                                false,
                                "story",
                                Map.of("ttl", "ttl_24h")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("story_batch_limit"));
    }

    @Test
    @DisplayName("TC-STORY-28: Max channel story → 422 story_unsupported_network")
    void tcStory28MaxUnsupported() throws Exception {
        String token = registerVerifyLogin("story-28-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "max", "missing");
        UUID mediaId = seedMedia(workspaceId);

        mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "contentKind",
                                "story",
                                "baseContent",
                                Map.of("text", "", "mediaIds", List.of(mediaId.toString())),
                                "scheduledAt",
                                Instant.now().plus(1, ChronoUnit.HOURS).toString(),
                                "ad",
                                false,
                                "story",
                                Map.of("ttl", "ttl_24h"),
                                "variants",
                                List.of(Map.of(
                                        "channelId",
                                        channel.getId().toString(),
                                        "network",
                                        "max",
                                        "content",
                                        Map.of("mediaIds", List.of(mediaId.toString()))))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("story_unsupported_network"));
    }

    private ChannelEntity seedChannel(UUID workspaceId, String network, String storyStatus) {
        ChannelEntity channel = new ChannelEntity();
        channel.setId(UuidV7.generate());
        channel.setWorkspaceId(workspaceId);
        channel.setNetwork(network);
        channel.setExternalId("ext-" + UUID.randomUUID());
        channel.setType("group");
        channel.setTitle("Test " + network);
        channel.setStatus("active");
        channel.setTimezone("Europe/Moscow");
        channel.setStoryConnectionStatus(storyStatus);
        channel.setCreatedAt(Instant.now());
        channel.setUpdatedAt(Instant.now());
        return channelRepository.save(channel);
    }

    private UUID seedMedia(UUID workspaceId) {
        return seedMedia(workspaceId, "image/jpeg", 2048L, "story.jpg");
    }

    private UUID seedMedia(UUID workspaceId, String mime, long sizeBytes, String filename) {
        MediaAssetEntity asset = new MediaAssetEntity();
        asset.setId(UuidV7.generate());
        asset.setWorkspaceId(workspaceId);
        String kind = mime != null && mime.toLowerCase(Locale.ROOT).startsWith("video/") ? "video" : "image";
        asset.setKind(kind);
        asset.setMime(mime);
        asset.setSizeBytes(sizeBytes);
        asset.setFilename(filename);
        asset.setS3Key("workspaces/" + workspaceId + "/media/" + asset.getId() + "/" + filename);
        asset.setStatus("ready");
        asset.setTags(List.of());
        asset.setDerivatives(Map.of());
        asset.setCreatedAt(Instant.now());
        asset.setUpdatedAt(Instant.now());
        return mediaAssetRepository.save(asset).getId();
    }

    private void upsertConnection(String token, UUID workspaceId, String provider, String apiKey) throws Exception {
        mockMvc.perform(put("/api/v1/workspaces/" + workspaceId + "/ord/connections/" + provider)
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "apiKey",
                                apiKey,
                                "publisherInn",
                                "7707083893",
                                "publisherName",
                                "ООО Паблишер",
                                "orgType",
                                "juridical",
                                "roles",
                                List.of("publisher"),
                                "isDefault",
                                true,
                                "responsibilityAccepted",
                                true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasApiKey").value(true))
                .andExpect(jsonPath("$.apiKey").doesNotExist());

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/ord/connections/" + provider + "/test")
                        .with(csrf())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private static Map<String, Object> validAdMarking() {
        return new LinkedHashMap<>(Map.of(
                "advertiserName",
                "ООО Рекламодатель",
                "inn",
                "7707083893",
                "kpp",
                "770701001",
                "orgType",
                "juridical",
                "amount",
                1500,
                "contractAmount",
                1500,
                "contractDate",
                "2026-01-15",
                "kktuCodes",
                List.of("1.1.1"),
                "roles",
                List.of("advertiser"),
                "selfPromo",
                false));
    }

    private String registerVerifyLogin(String email, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "Password1!Password1!", "name", name))))
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
        return login(email);
    }

    private String login(String email) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "Password1!Password1!"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper
                .readTree(login.getResponse().getContentAsString())
                .path("accessToken")
                .asText();
    }

    private UUID firstWorkspaceId(String token) throws Exception {
        JsonNode list = readJson(mockMvc.perform(get("/api/v1/workspaces").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn());
        UUID workspaceId = UUID.fromString(list.path("items").get(0).path("id").asText());
        // V15 free plan stories_month=0; story ITs need a paid plan (same as StoriesApiIntegrationTest).
        upgradeToAgency(workspaceId);
        return workspaceId;
    }

    private void upgradeToAgency(UUID workspaceId) {
        PlanEntity agency = planRepository.findByCode("agency").orElseThrow();
        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        workspace.setPlanId(agency.getId());
        workspaceRepository.save(workspace);
    }

    private void inviteAndAccept(String ownerToken, UUID workspaceId, String email, String role, List<UUID> projectIds)
            throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .with(csrf())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "role", role,
                                "projectIds", projectIds))))
                .andExpect(status().isCreated());
        String inviteToken = latestMailToken(email);
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .with(csrf())
                        .header("Authorization", bearer(login(email)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", inviteToken))))
                .andExpect(status().isOk());
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
                .orElseThrow(() -> new AssertionError("No mail for " + email));
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
