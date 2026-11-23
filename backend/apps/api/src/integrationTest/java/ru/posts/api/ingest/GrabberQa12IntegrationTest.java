package ru.posts.api.ingest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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
import ru.posts.persistence.entity.AuditLogEntity;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.GrabberItemEntity;
import ru.posts.persistence.entity.GrabberRunEntity;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.PostEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.repository.AuditLogRepository;
import ru.posts.persistence.repository.AutomationRuleRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.GrabberItemRepository;
import ru.posts.persistence.repository.GrabberRunRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.PostRepository;
import ru.posts.persistence.repository.PublicationRepository;
import ru.posts.persistence.repository.WorkspaceRepository;

/**
 * QA-12 extended TC-GRAB coverage (P0 gaps + P1). Complements {@link GrabberIngestIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class GrabberQa12IntegrationTest {

    private static final Pattern TOKEN_IN_BODY = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final String PASSWORD = "Password1!Password1!";

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
    GrabberRunRepository runRepository;

    @Autowired
    PublicationRepository publicationRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    AutomationRuleRepository ruleRepository;

    @Autowired
    GrabberRunProcessor runProcessor;

    @Autowired
    PlanRepository planRepository;

    @Autowired
    WorkspaceRepository workspaceRepository;

    @BeforeEach
    void setUp() {
        mailSender.clear();
        wireMock.resetAll();
        publicationRepository.deleteAll();
        itemRepository.deleteAll();
        runRepository.deleteAll();
        stubVkWall(
                """
                {"response":{"count":1,"items":[
                  {"id":10,"owner_id":-123,"text":"Grabbed post A","marked_as_ads":0,"attachments":[]}
                ]}}
                """);
    }

    @Test
    @DisplayName("TC-GRAB-02/13 scheduled run advances next_run_at; redelivery of finished run is no-op")
    void scheduledRunAndRedelivery() throws Exception {
        Fixture fx = bootstrap();
        JsonNode rule = createActiveRule(fx, Map.of("sourceIds", List.of("-123"), "scanIntervalMinutes", 15));
        UUID ruleId = UUID.fromString(rule.get("id").asText());

        GrabberRunEntity first = runProcessor.process(ruleId, null, "scheduled", "trace-1");
        assertThat(first.getTrigger()).isEqualTo("scheduled");
        assertThat(first.getStatus()).isEqualTo("succeeded");
        assertThat(first.getItemsNew()).isGreaterThanOrEqualTo(1);
        Instant next = ruleRepository.findById(ruleId).orElseThrow().getNextRunAt();
        assertThat(next).isAfter(Instant.now().minusSeconds(5));

        long itemsAfterFirst =
                itemRepository.findByRuleIdOrderByDetectedAtDesc(ruleId).size();
        GrabberRunEntity again = runProcessor.process(ruleId, first.getId(), "scheduled", "trace-1");
        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(again.getFinishedAt()).isNotNull();
        assertThat(itemRepository.findByRuleIdOrderByDetectedAtDesc(ruleId)).hasSize((int) itemsAfterFirst);
        assertThat(publicationRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("TC-GRAB-03/05 second manual run within 5m → 429; scheduled re-ingest dedups remote")
    void manualRateLimitAndRemoteDedup() throws Exception {
        Fixture fx = bootstrap();
        JsonNode rule = createActiveRule(fx, Map.of("sourceIds", List.of("-123"), "scanIntervalMinutes", 15));
        UUID ruleId = UUID.fromString(rule.get("id").asText());

        mockMvc.perform(post("/api/v1/ingest/rules/" + ruleId + "/runs")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .header("Idempotency-Key", "run-" + UUID.randomUUID()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.itemsNew").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(post("/api/v1/ingest/rules/" + ruleId + "/runs")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .header("Idempotency-Key", "run-" + UUID.randomUUID()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ingest_rate_limited"));

        GrabberRunEntity second = runProcessor.process(ruleId, null, "scheduled", "trace-dedup");
        assertThat(second.getItemsDeduped()).isGreaterThanOrEqualTo(1);
        assertThat(second.getItemsNew()).isZero();
        assertThat(itemRepository.findByRuleIdOrderByDetectedAtDesc(ruleId)).hasSize(1);
    }

    @Test
    @DisplayName("TC-GRAB-04/09 attribution footer on approve; items history has permalink + postId")
    void attributionAndHistory() throws Exception {
        Fixture fx = bootstrap();
        JsonNode rule = createActiveRule(fx, Map.of("sourceIds", List.of("-123"), "scanIntervalMinutes", 15));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        runProcessor.process(ruleId, null, "manual", "trace-attr");

        JsonNode items = readJson(mockMvc.perform(get("/api/v1/ingest/rules/" + ruleId + "/items")
                        .header("Authorization", bearer(fx.token))
                        .param("status", "pending_moderation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sourcePermalink").exists())
                .andReturn());
        UUID itemId = UUID.fromString(items.get("items").get(0).get("id").asText());

        JsonNode approved = readJson(mockMvc.perform(post("/api/v1/ingest/items/" + itemId + "/approve")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .header("Idempotency-Key", "approve-" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").exists())
                .andReturn());

        UUID postId = UUID.fromString(approved.get("postId").asText());
        PostEntity post = postRepository.findById(postId).orElseThrow();
        Object text = post.getBaseContent().get("text");
        assertThat(String.valueOf(text)).contains("Источник:").contains("vk.com/wall");

        mockMvc.perform(get("/api/v1/ingest/rules/" + ruleId + "/items").header("Authorization", bearer(fx.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].postId").value(postId.toString()));
        assertThat(publicationRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("TC-GRAB-11 optional auto publish creates publication without approve")
    void autoPublishMode() throws Exception {
        Fixture fx = bootstrap();
        JsonNode rule = createRule(
                fx,
                Map.of(
                        "type",
                        "grabber",
                        "name",
                        "Auto",
                        "targetChannelId",
                        fx.channelId.toString(),
                        "userResponsibilityAccepted",
                        true,
                        "activate",
                        true,
                        "config",
                        Map.of("sourceIds", List.of("-123"), "scanIntervalMinutes", 15, "publishMode", "auto")));
        assertThat(rule.get("config").get("publishMode").asText()).isEqualTo("auto");
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        GrabberRunEntity run = runProcessor.process(ruleId, null, "manual", "trace-auto");
        assertThat(run.getItemsPublished()).isGreaterThanOrEqualTo(1);
        assertThat(publicationRepository.findAll()).hasSize(1);
        assertThat(itemRepository
                        .findByRuleIdOrderByDetectedAtDesc(ruleId)
                        .getFirst()
                        .getStatus())
                .isIn("scheduled", "published", "approved");
    }

    @Test
    @DisplayName("TC-GRAB-15 Author cannot manage/moderate; Viewer cannot moderate")
    void rbacAuthorViewer() throws Exception {
        Fixture fx = bootstrap();
        upgradeToAgency(fx.workspaceId);
        String authorEmail = "author-" + UUID.randomUUID() + "@example.com";
        String viewerEmail = "viewer-" + UUID.randomUUID() + "@example.com";
        String authorToken = registerVerifyLogin(authorEmail, "Author");
        String viewerToken = registerVerifyLogin(viewerEmail, "Viewer");
        inviteAndAccept(fx.token, fx.workspaceId, authorEmail, "author", List.of(fx.projectId));
        inviteAndAccept(fx.token, fx.workspaceId, viewerEmail, "viewer", List.of(fx.projectId));

        mockMvc.perform(post("/api/v1/workspaces/" + fx.workspaceId + "/projects/" + fx.projectId + "/ingest/rules")
                        .with(csrf())
                        .header("Authorization", bearer(authorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type",
                                "grabber",
                                "name",
                                "Denied",
                                "targetChannelId",
                                fx.channelId.toString(),
                                "userResponsibilityAccepted",
                                true,
                                "config",
                                Map.of("sourceIds", List.of("-123"), "scanIntervalMinutes", 60)))))
                .andExpect(status().isForbidden());

        JsonNode rule = createActiveRule(fx, Map.of("sourceIds", List.of("-123"), "scanIntervalMinutes", 15));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        runProcessor.process(ruleId, null, "manual", "trace-rbac");
        UUID itemId = itemRepository
                .findByRuleIdOrderByDetectedAtDesc(ruleId)
                .getFirst()
                .getId();

        mockMvc.perform(get("/api/v1/ingest/rules/" + ruleId + "/items").header("Authorization", bearer(authorToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ingest/items/" + itemId + "/approve")
                        .with(csrf())
                        .header("Authorization", bearer(authorToken))
                        .header("Idempotency-Key", "a-" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ingest/items/" + itemId + "/approve")
                        .with(csrf())
                        .header("Authorization", bearer(viewerToken))
                        .header("Idempotency-Key", "v-" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Retry of publish_failed without automations.moderate is 403")
    void retryWithoutModerateIsForbidden() throws Exception {
        Fixture fx = bootstrap();
        upgradeToAgency(fx.workspaceId);
        String viewerEmail = "viewer-retry-" + UUID.randomUUID() + "@example.com";
        String viewerToken = registerVerifyLogin(viewerEmail, "Viewer");
        inviteAndAccept(fx.token, fx.workspaceId, viewerEmail, "viewer", List.of(fx.projectId));

        JsonNode rule = createActiveRule(fx, Map.of("sourceIds", List.of("-123"), "scanIntervalMinutes", 15));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        runProcessor.process(ruleId, null, "manual", "trace-retry-rbac");
        GrabberItemEntity item =
                itemRepository.findByRuleIdOrderByDetectedAtDesc(ruleId).getFirst();
        item.setStatus("publish_failed");
        item.setSkipReason("video_reupload_failed:video_private");
        itemRepository.save(item);

        mockMvc.perform(post("/api/v1/ingest/items/" + item.getId() + "/approve")
                        .with(csrf())
                        .header("Authorization", bearer(viewerToken))
                        .header("Idempotency-Key", "retry-" + item.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-GRAB-17 audit create/activate/approve")
    void auditTrail() throws Exception {
        Fixture fx = bootstrap();
        JsonNode rule = createActiveRule(fx, Map.of("sourceIds", List.of("-123"), "scanIntervalMinutes", 15));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        runProcessor.process(ruleId, null, "manual", "trace-audit");
        UUID itemId = itemRepository
                .findByRuleIdOrderByDetectedAtDesc(ruleId)
                .getFirst()
                .getId();
        mockMvc.perform(post("/api/v1/ingest/items/" + itemId + "/approve")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .header("Idempotency-Key", "audit-" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        List<String> actions = auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(fx.workspaceId).stream()
                .map(AuditLogEntity::getAction)
                .toList();
        assertThat(actions).contains("ingest.rule.created", "ingest.rule.activated", "ingest.item.approved");
    }

    @Test
    @DisplayName("TC-GRAB-18 auto_pause after N failed runs")
    void autoPauseOnErrors() throws Exception {
        Fixture fx = bootstrap();
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.get"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));
        JsonNode rule = createRule(
                fx,
                Map.of(
                        "type",
                        "grabber",
                        "name",
                        "Failing",
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
                                "reliability",
                                Map.of(
                                        "auto_pause_on_errors",
                                        true,
                                        "auto_pause_error_threshold",
                                        3,
                                        "dedup_enabled",
                                        true))));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        for (int i = 0; i < 3; i++) {
            runProcessor.process(ruleId, null, "scheduled", "fail-" + i);
        }
        assertThat(ruleRepository.findById(ruleId).orElseThrow().getStatus()).isEqualTo("auto_paused");
        assertThat(ruleRepository.findById(ruleId).orElseThrow().getConsecutiveFailures())
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("TC-GRAB-19 filters exclude ads → filtered_out")
    void filterAds() throws Exception {
        Fixture fx = bootstrap();
        stubVkWall(
                """
                {"response":{"count":1,"items":[
                  {"id":77,"owner_id":-123,"text":"Promo","marked_as_ads":1,"attachments":[]}
                ]}}
                """);
        JsonNode rule = createActiveRule(
                fx,
                Map.of(
                        "sourceIds",
                        List.of("-123"),
                        "scanIntervalMinutes",
                        15,
                        "filters",
                        Map.of("exclude_ads", true, "exclude_reposts", true)));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        runProcessor.process(ruleId, null, "manual", "trace-filter");
        GrabberItemEntity item =
                itemRepository.findByRuleIdOrderByDetectedAtDesc(ruleId).getFirst();
        assertThat(item.getStatus()).isEqualTo("filtered_out");
        assertThat(item.getSkipReason()).isEqualTo("exclude_ads");
        assertThat(publicationRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("TC-GRAB-20/11 auto rate_limit leaves item pending with skip_reason")
    void autoRateLimitQueues() throws Exception {
        Fixture fx = bootstrap();
        stubVkWall(
                """
                {"response":{"count":3,"items":[
                  {"id":1,"owner_id":-123,"text":"A1","marked_as_ads":0,"attachments":[]},
                  {"id":2,"owner_id":-123,"text":"A2","marked_as_ads":0,"attachments":[]},
                  {"id":3,"owner_id":-123,"text":"A3","marked_as_ads":0,"attachments":[]}
                ]}}
                """);
        JsonNode rule = createRule(
                fx,
                Map.of(
                        "type",
                        "grabber",
                        "name",
                        "Rate",
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
                                "limits",
                                Map.of("rate_limit_per_hour", 2, "rate_limit_per_day", 50))));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        runProcessor.process(ruleId, null, "manual", "trace-rate");
        long rateLimited = itemRepository.findByRuleIdOrderByDetectedAtDesc(ruleId).stream()
                .filter(i -> "rate_limit".equals(i.getSkipReason()))
                .count();
        assertThat(rateLimited).isGreaterThanOrEqualTo(1);
        assertThat(publicationRepository.findAll().size()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("TC-GRAB-21/12 website XSS sanitized; extra SSRF schemes blocked")
    void websiteXssAndExtraSsrf() throws Exception {
        Fixture fx = bootstrap();
        String html = readFixture("article-xss.html");
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/xss.html"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody(html)));

        for (String badUrl : List.of(
                "file:///etc/passwd",
                "ftp://example.com/x",
                "http://[::1]/",
                "http://172.16.5.5/internal",
                "http://169.254.169.254/latest/meta-data")) {
            mockMvc.perform(post("/api/v1/workspaces/" + fx.workspaceId + "/projects/" + fx.projectId + "/ingest/rules")
                            .with(csrf())
                            .header("Authorization", bearer(fx.token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "type",
                                    "website",
                                    "name",
                                    "SSRF",
                                    "targetChannelId",
                                    fx.channelId.toString(),
                                    "userResponsibilityAccepted",
                                    true,
                                    "config",
                                    Map.of("websiteUrls", List.of(badUrl))))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("ingest_ssrf_blocked"));
        }

        JsonNode rule = createRule(
                fx,
                Map.of(
                        "type",
                        "website",
                        "name",
                        "XSS site",
                        "targetChannelId",
                        fx.channelId.toString(),
                        "userResponsibilityAccepted",
                        true,
                        "activate",
                        true,
                        "config",
                        Map.of(
                                "websiteUrls",
                                List.of(wireMock.baseUrl() + "/xss.html"),
                                "websiteSelector",
                                "article",
                                "scanIntervalMinutes",
                                15)));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        runProcessor.process(ruleId, null, "manual", "trace-xss");
        GrabberItemEntity item =
                itemRepository.findByRuleIdOrderByDetectedAtDesc(ruleId).getFirst();
        assertThat(item.getStatus()).isEqualTo("pending_moderation");
        assertThat(item.getContentHtml())
                .doesNotContain("<script")
                .doesNotContain("onerror")
                .doesNotContain("javascript:");
        assertThat(item.getContentText()).contains("XSS Article Title");
    }

    @Test
    @DisplayName("TC-GRAB-24/27 bulk approve + edit-and-approve keep attribution")
    void bulkAndEditApprove() throws Exception {
        Fixture fx = bootstrap();
        stubVkWall(
                """
                {"response":{"count":2,"items":[
                  {"id":1,"owner_id":-123,"text":"One","marked_as_ads":0,"attachments":[]},
                  {"id":2,"owner_id":-123,"text":"Two","marked_as_ads":0,"attachments":[]}
                ]}}
                """);
        JsonNode rule = createActiveRule(fx, Map.of("sourceIds", List.of("-123"), "scanIntervalMinutes", 15));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        runProcessor.process(ruleId, null, "manual", "trace-bulk");
        List<GrabberItemEntity> pending =
                itemRepository.findByRuleIdAndStatusOrderByDetectedAtDesc(ruleId, "pending_moderation");
        assertThat(pending).hasSizeGreaterThanOrEqualTo(2);

        UUID editId = pending.get(0).getId();
        UUID bulkId = pending.get(1).getId();

        JsonNode edited = readJson(mockMvc.perform(post("/api/v1/ingest/items/" + editId + "/edit-and-approve")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .header("Idempotency-Key", "edit-" + editId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("contentText", "Edited body"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").exists())
                .andReturn());
        PostEntity editedPost = postRepository
                .findById(UUID.fromString(edited.get("postId").asText()))
                .orElseThrow();
        assertThat(String.valueOf(editedPost.getBaseContent().get("text")))
                .contains("Edited body")
                .contains("Источник:");

        mockMvc.perform(post("/api/v1/ingest/items/bulk")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("itemIds", List.of(bulkId.toString()), "action", "approve"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1));

        List<String> actions = auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(fx.workspaceId).stream()
                .map(AuditLogEntity::getAction)
                .toList();
        assertThat(actions).contains("ingest.item.edit_approved", "ingest.bulk_moderated");
        assertThat(publicationRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("TC-GRAB-25 text over VK limit → failed_validation, no publication")
    void validationLimit() throws Exception {
        Fixture fx = bootstrap();
        String huge = "x".repeat(5000);
        stubVkWall(
                """
                {"response":{"count":1,"items":[
                  {"id":9,"owner_id":-123,"text":"%s","marked_as_ads":0,"attachments":[]}
                ]}}
                """
                        .formatted(huge));
        JsonNode rule = createActiveRule(fx, Map.of("sourceIds", List.of("-123"), "scanIntervalMinutes", 15));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        runProcessor.process(ruleId, null, "manual", "trace-val");
        UUID itemId = itemRepository
                .findByRuleIdOrderByDetectedAtDesc(ruleId)
                .getFirst()
                .getId();
        mockMvc.perform(post("/api/v1/ingest/items/" + itemId + "/approve")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .header("Idempotency-Key", "val-" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        GrabberItemEntity item = itemRepository.findById(itemId).orElseThrow();
        assertThat(item.getStatus()).isEqualTo("failed_validation");
        assertThat(item.getSkipReason()).isEqualTo("text_too_long");
        assertThat(item.getPostId()).isNull();
        assertThat(publicationRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("TC-GRAB-26 channel reauth_required → auto_paused")
    void channelReauthAutoPause() throws Exception {
        Fixture fx = bootstrap();
        JsonNode rule = createActiveRule(fx, Map.of("sourceIds", List.of("-123"), "scanIntervalMinutes", 15));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        ChannelEntity channel = channelRepository.findById(fx.channelId).orElseThrow();
        channel.setStatus("reauth_required");
        channelRepository.save(channel);
        GrabberRunEntity run = runProcessor.process(ruleId, null, "scheduled", "trace-reauth");
        assertThat(run.getStatus()).isEqualTo("failed");
        assertThat(run.getErrorCode()).isEqualTo("channel_reauth_required");
        assertThat(ruleRepository.findById(ruleId).orElseThrow().getStatus()).isEqualTo("auto_paused");
    }

    @Test
    @Tag("nightly")
    @DisplayName("TC-GRAB-14 publish path keeps item/post; 0 duplicate publications on re-approve")
    void pipelineLinkageNoDuplicate() throws Exception {
        Fixture fx = bootstrap();
        JsonNode rule = createActiveRule(fx, Map.of("sourceIds", List.of("-123"), "scanIntervalMinutes", 15));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        runProcessor.process(ruleId, null, "manual", "trace-pipe");
        UUID itemId = itemRepository
                .findByRuleIdOrderByDetectedAtDesc(ruleId)
                .getFirst()
                .getId();
        mockMvc.perform(post("/api/v1/ingest/items/" + itemId + "/approve")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .header("Idempotency-Key", "pipe-" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        List<PublicationEntity> pubs = publicationRepository.findAll();
        assertThat(pubs).hasSize(1);
        assertThat(pubs.getFirst().getState()).isIn("pending", "queued", "succeeded");
        GrabberItemEntity item = itemRepository.findById(itemId).orElseThrow();
        assertThat(item.getPostId()).isNotNull();
        assertThat(postRepository.findById(item.getPostId())).isPresent();

        mockMvc.perform(post("/api/v1/ingest/items/" + itemId + "/approve")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .header("Idempotency-Key", "pipe-again-" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
        assertThat(publicationRepository.findAll()).hasSize(1);
    }

    private void stubVkWall(String body) {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.get"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private JsonNode createActiveRule(Fixture fx, Map<String, Object> config) throws Exception {
        return createRule(
                fx,
                Map.of(
                        "type",
                        "grabber",
                        "name",
                        "Rule " + UUID.randomUUID(),
                        "targetChannelId",
                        fx.channelId.toString(),
                        "userResponsibilityAccepted",
                        true,
                        "activate",
                        true,
                        "config",
                        config));
    }

    private JsonNode createRule(Fixture fx, Map<String, Object> body) throws Exception {
        return readJson(mockMvc.perform(
                        post("/api/v1/workspaces/" + fx.workspaceId + "/projects/" + fx.projectId + "/ingest/rules")
                                .with(csrf())
                                .header("Authorization", bearer(fx.token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private Fixture bootstrap() throws Exception {
        String token = registerVerifyLogin("grab-qa12-" + UUID.randomUUID() + "@example.com", "Grab Owner");
        UUID workspaceId = firstWorkspaceId(token);
        upgradeToAgency(workspaceId);
        JsonNode project = readJson(mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .with(csrf())
                        .header("Authorization", bearer(token))
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
        String inviteToken = mailSender.messages().stream()
                .filter(m -> m.to().equalsIgnoreCase(email))
                .reduce((a, b) -> b)
                .map(m -> {
                    Matcher matcher = TOKEN_IN_BODY.matcher(m.body());
                    assertThat(matcher.find()).isTrue();
                    return matcher.group(1);
                })
                .orElseThrow();
        String memberToken = login(email);
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .with(csrf())
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", inviteToken))))
                .andExpect(status().isOk());
    }

    private String registerVerifyLogin(String email, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD,
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
                .orElseThrow();
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
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
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

    private static String readFixture(String name) throws Exception {
        Path repoRoot = Path.of(System.getProperty("posts.repo.root", "."))
                .toAbsolutePath()
                .normalize();
        Path path = repoRoot.resolve("tests/fixtures/grabber").resolve(name);
        if (!Files.exists(path)) {
            path = Path.of("tests/fixtures/grabber")
                    .resolve(name)
                    .toAbsolutePath()
                    .normalize();
        }
        if (!Files.exists(path)) {
            // fallback: relative from backend/
            path = Path.of("../tests/fixtures/grabber")
                    .resolve(name)
                    .toAbsolutePath()
                    .normalize();
        }
        return Files.readString(path);
    }

    private record Fixture(String token, UUID workspaceId, UUID projectId, UUID channelId) {}
}
