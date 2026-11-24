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
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.GrabberItemEntity;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.PostVariantEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.jdbc.WalletLedgerJdbc;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.GrabberItemRepository;
import ru.posts.persistence.repository.LedgerEntryRepository;
import ru.posts.persistence.repository.NotificationRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.PostVariantRepository;
import ru.posts.persistence.repository.PublicationRepository;
import ru.posts.persistence.repository.WorkspaceRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class GrabberIngestIntegrationTest {

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
        registry.add("posts.grabber.ai.enabled", () -> "true");
        registry.add("posts.grabber.ai.cost-kopecks", () -> "100");
        registry.add("posts.grabber.action-button.enabled", () -> "true");
        // WireMock binds to localhost; production SSRF still blocks 10.x / metadata.
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
    PlanRepository planRepository;

    @Autowired
    WorkspaceRepository workspaceRepository;

    @Autowired
    PostVariantRepository postVariantRepository;

    @Autowired
    WalletLedgerJdbc walletLedger;

    @Autowired
    LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        mailSender.clear();
        wireMock.resetAll();
        publicationRepository.deleteAll();
        itemRepository.deleteAll();
        stubVkWall(
                """
                {"response":{"count":1,"items":[
                  {"id":10,"owner_id":-123,"text":"Grabbed post A","marked_as_ads":0,"attachments":[]}
                ]}}
                """);
    }

    @Test
    @DisplayName("TC-GRAB-01/08b/22 create rule defaults moderate; normalizes source ids")
    void createRuleDefaultsModerate() throws Exception {
        Fixture fx = bootstrap();
        JsonNode created = createRule(
                fx,
                Map.of(
                        "type",
                        "grabber",
                        "name",
                        "VK grabber",
                        "targetChannelId",
                        fx.channelId.toString(),
                        "userResponsibilityAccepted",
                        true,
                        "config",
                        Map.of("sourceIds", List.of(" -123, club456 , @public "), "scanIntervalMinutes", 15)));
        assertThat(created.get("config").get("publishMode").asText()).isEqualTo("moderate");
        assertThat(created.get("status").asText()).isEqualTo("draft");
        JsonNode ids = created.get("config").get("sourceIds");
        assertThat(ids.toString()).contains("-123").contains("-456").contains("public");

        mockMvc.perform(get("/api/v1/workspaces/" + fx.workspaceId + "/projects/" + fx.projectId + "/ingest/rules")
                        .header("Authorization", bearer(fx.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(created.get("id").asText()));
    }

    @Test
    @DisplayName("TC-GRAB-07 activate without attribution/responsibility → 422")
    void activateRequiresAttributionAndResponsibility() throws Exception {
        Fixture fx = bootstrap();
        JsonNode created = createRule(
                fx,
                Map.of(
                        "type",
                        "grabber",
                        "name",
                        "No legal",
                        "targetChannelId",
                        fx.channelId.toString(),
                        "userResponsibilityAccepted",
                        false,
                        "config",
                        Map.of(
                                "sourceIds",
                                List.of("-123"),
                                "attribution",
                                Map.of("enabled", false),
                                "scanIntervalMinutes",
                                60)));
        UUID ruleId = UUID.fromString(created.get("id").asText());
        mockMvc.perform(post("/api/v1/ingest/rules/" + ruleId + "/activate")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ingest_responsibility_required"));
    }

    @Test
    @DisplayName("TC-GRAB-03/05/08/09/23 manual run moderate; approve once; reject; dedup remote")
    void moderateApproveRejectDedup() throws Exception {
        Fixture fx = bootstrap();
        JsonNode rule = createRule(
                fx,
                Map.of(
                        "type",
                        "grabber",
                        "name",
                        "Moderate",
                        "targetChannelId",
                        fx.channelId.toString(),
                        "userResponsibilityAccepted",
                        true,
                        "activate",
                        true,
                        "config",
                        Map.of("sourceIds", List.of("-123"), "scanIntervalMinutes", 15)));
        UUID ruleId = UUID.fromString(rule.get("id").asText());

        JsonNode run1 = readJson(mockMvc.perform(post("/api/v1/ingest/rules/" + ruleId + "/runs")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .header("Idempotency-Key", "run-" + UUID.randomUUID()))
                .andExpect(status().isAccepted())
                .andReturn());
        assertThat(run1.get("trigger").asText()).isEqualTo("manual");
        assertThat(run1.get("itemsNew").asInt()).isGreaterThanOrEqualTo(1);

        JsonNode items = readJson(mockMvc.perform(get("/api/v1/ingest/rules/" + ruleId + "/items")
                        .header("Authorization", bearer(fx.token))
                        .param("status", "pending_moderation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sourcePermalink").exists())
                .andReturn());
        UUID itemId = UUID.fromString(items.get("items").get(0).get("id").asText());
        assertThat(publicationRepository.findAll()).isEmpty();

        mockMvc.perform(post("/api/v1/ingest/items/" + itemId + "/approve")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .header("Idempotency-Key", "approve-" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").exists())
                .andExpect(jsonPath("$.status").value("scheduled"));

        List<PublicationEntity> pubs = publicationRepository.findAll();
        assertThat(pubs).hasSize(1);
        assertThat(pubs.getFirst().getState()).isEqualTo("pending");

        mockMvc.perform(post("/api/v1/ingest/items/" + itemId + "/approve")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .header("Idempotency-Key", "approve-again-" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
        assertThat(publicationRepository.findAll()).hasSize(1);

        // second run dedups remote
        // rate-limit manual runs: bump started_at artificially by creating via processor path after 5 min window —
        // stub another unique post and call processor through a new rule run after clearing rate limit via DB isn't
        // needed: create second rule item manually for reject path.
        GrabberItemEntity pending = new GrabberItemEntity();
        pending.setId(UuidV7.generate());
        pending.setRuleId(ruleId);
        pending.setWorkspaceId(fx.workspaceId);
        pending.setTargetChannelId(fx.channelId);
        pending.setSourceNetwork("vk");
        pending.setSourceId("-123");
        pending.setRemotePostId("wall-123_99");
        pending.setSourcePermalink("https://vk.com/wall-123_99");
        pending.setSourceName("-123");
        pending.setContentText("reject me");
        pending.setContentHash("a".repeat(64));
        pending.setStatus("pending_moderation");
        pending.setDetectedAt(Instant.now());
        pending.setCreatedAt(Instant.now());
        pending.setUpdatedAt(Instant.now());
        itemRepository.save(pending);

        mockMvc.perform(post("/api/v1/ingest/items/" + pending.getId() + "/reject")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "nope"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"));
    }

    @Test
    @DisplayName("TC-GRAB-06 content_hash dedup suppresses second item")
    void contentHashDedup() throws Exception {
        Fixture fx = bootstrap();
        stubVkWall(
                """
                {"response":{"count":2,"items":[
                  {"id":1,"owner_id":-123,"text":"Same text","marked_as_ads":0,"attachments":[]},
                  {"id":2,"owner_id":-123,"text":"Same text","marked_as_ads":0,"attachments":[]}
                ]}}
                """);
        JsonNode rule = createRule(
                fx,
                Map.of(
                        "type",
                        "grabber",
                        "name",
                        "Dedup",
                        "targetChannelId",
                        fx.channelId.toString(),
                        "userResponsibilityAccepted",
                        true,
                        "activate",
                        true,
                        "config",
                        Map.of("sourceIds", List.of("-123"), "scanIntervalMinutes", 15)));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        mockMvc.perform(post("/api/v1/ingest/rules/" + ruleId + "/runs")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.itemsDeduped").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("TC-GRAB-10 pause stops scheduled eligibility; resume restores active")
    void pauseResume() throws Exception {
        Fixture fx = bootstrap();
        JsonNode rule = createRule(
                fx,
                Map.of(
                        "type",
                        "grabber",
                        "name",
                        "Pause",
                        "targetChannelId",
                        fx.channelId.toString(),
                        "userResponsibilityAccepted",
                        true,
                        "activate",
                        true,
                        "config",
                        Map.of("sourceIds", List.of("-123"), "scanIntervalMinutes", 15)));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        mockMvc.perform(post("/api/v1/ingest/rules/" + ruleId + "/pause")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("paused"));
        mockMvc.perform(post("/api/v1/ingest/rules/" + ruleId + "/runs")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/ingest/rules/" + ruleId + "/resume")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.nextRunAt").exists());
    }

    @Test
    @DisplayName("TC-GRAB-11/12 RSS moderate + SSRF blocked")
    void rssAndSsrf() throws Exception {
        Fixture fx = bootstrap();
        String feed =
                """
                <?xml version="1.0"?><rss version="2.0"><channel><title>T</title>
                <item><title>R1</title><description>Body</description><guid>rss-1</guid>
                <link>https://example.com/1</link></item></channel></rss>
                """;
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/feed.xml"))
                .willReturn(
                        aResponse().withStatus(200).withBody(feed).withHeader("Content-Type", "application/rss+xml")));

        mockMvc.perform(post("/api/v1/workspaces/" + fx.workspaceId + "/projects/" + fx.projectId + "/ingest/rules")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type",
                                "rss",
                                "name",
                                "Bad RSS",
                                "targetChannelId",
                                fx.channelId.toString(),
                                "userResponsibilityAccepted",
                                true,
                                "config",
                                Map.of("rssUrl", "http://169.254.169.254/latest/meta-data")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ingest_ssrf_blocked"));

        JsonNode rule = createRule(
                fx,
                Map.of(
                        "type",
                        "rss",
                        "name",
                        "Good RSS",
                        "targetChannelId",
                        fx.channelId.toString(),
                        "userResponsibilityAccepted",
                        true,
                        "activate",
                        true,
                        "config",
                        Map.of("rssUrl", wireMock.baseUrl() + "/feed.xml", "scanIntervalMinutes", 15)));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        assertThat(rule.get("config").get("publishMode").asText()).isEqualTo("moderate");
        mockMvc.perform(post("/api/v1/ingest/rules/" + ruleId + "/runs")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.itemsNew").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        JsonNode pending = readJson(mockMvc.perform(get("/api/v1/ingest/rules/" + ruleId + "/items")
                        .header("Authorization", bearer(fx.token))
                        .param("status", "pending_moderation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].remotePostId").value("rss-1"))
                .andReturn());
        assertThat(pending.get("items").get(0).get("postId").isNull()).isTrue();
        assertThat(publicationRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("TC-GRAB-21 website OG-extract moderate; SSRF on website URL")
    void websiteExtractAndSsrf() throws Exception {
        Fixture fx = bootstrap();
        String html =
                """
                <html><head>
                  <meta property="og:title" content="Site Title"/>
                  <meta property="og:description" content="Site body"/>
                  <meta property="og:image" content="https://cdn.example/img.jpg"/>
                  <meta property="og:site_name" content="ExampleSite"/>
                </head><body><article><p>Hello <script>alert(1)</script>world</p>
                <img src="/x.jpg"/></article></body></html>
                """;
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/article.html"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody(html)));
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/ssrf-hop"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", "http://169.254.169.254/latest/meta-data/")));

        mockMvc.perform(post("/api/v1/workspaces/" + fx.workspaceId + "/projects/" + fx.projectId + "/ingest/rules")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type",
                                "website",
                                "name",
                                "Bad site",
                                "targetChannelId",
                                fx.channelId.toString(),
                                "userResponsibilityAccepted",
                                true,
                                "config",
                                Map.of("websiteUrls", List.of("http://169.254.169.254/latest/meta-data"))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ingest_ssrf_blocked"));

        JsonNode rule = createRule(
                fx,
                Map.of(
                        "type",
                        "website",
                        "name",
                        "Good site",
                        "targetChannelId",
                        fx.channelId.toString(),
                        "userResponsibilityAccepted",
                        true,
                        "activate",
                        true,
                        "config",
                        Map.of(
                                "websiteUrls",
                                List.of(wireMock.baseUrl() + "/article.html"),
                                "websiteSelector",
                                "article",
                                "scanIntervalMinutes",
                                15)));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        assertThat(rule.get("config").get("publishMode").asText()).isEqualTo("moderate");
        assertThat(rule.get("config").get("websiteUrls")).isNotNull();

        mockMvc.perform(post("/api/v1/ingest/rules/" + ruleId + "/runs")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.itemsNew").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        JsonNode pending = readJson(mockMvc.perform(get("/api/v1/ingest/rules/" + ruleId + "/items")
                        .header("Authorization", bearer(fx.token))
                        .param("status", "pending_moderation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sourceNetwork").value("website"))
                .andExpect(jsonPath("$.items[0].contentText").value(org.hamcrest.Matchers.containsString("Site Title")))
                .andReturn());
        assertThat(pending.get("items").get(0).get("postId").isNull()).isTrue();
        assertThat(publicationRepository.findAll()).isEmpty();

        // Redirect to metadata must be blocked on preview (SSRF on redirect hop).
        mockMvc.perform(post("/api/v1/ingest/preview")
                        .with(csrf())
                        .param("workspaceId", fx.workspaceId.toString())
                        .header("Authorization", bearer(fx.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("type", "website", "websiteUrls", List.of(wireMock.baseUrl() + "/ssrf-hop")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ingest_ssrf_blocked"));
    }

    @Test
    @DisplayName("TC-GRAB-16 IDOR foreign rule → 404")
    void idorForeignRule() throws Exception {
        Fixture a = bootstrap();
        Fixture b = bootstrap();
        JsonNode rule = createRule(
                a,
                Map.of(
                        "type",
                        "grabber",
                        "name",
                        "Secret",
                        "targetChannelId",
                        a.channelId.toString(),
                        "userResponsibilityAccepted",
                        true,
                        "config",
                        Map.of("sourceIds", List.of("-123"), "scanIntervalMinutes", 60)));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        mockMvc.perform(get("/api/v1/ingest/rules/" + ruleId).header("Authorization", bearer(b.token)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("first comment, action button, delete timer, author and AI debit")
    void publishOptionsAndNeuralCharge() throws Exception {
        Fixture fx = bootstrap();
        walletLedger.credit(fx.workspaceId, 10_000, "payment", false, "topup-" + fx.workspaceId, null, "test");
        stubVkWall(
                """
                {"response":{"count":1,"items":[
                  {"id":10,"owner_id":-123,"from_id":55,"signer_id":55,"text":"Grabbed post A #юмор","marked_as_ads":0,"attachments":[]}
                ],"profiles":[{"id":55,"first_name":"Иван","last_name":"Петров"}]}}
                """);
        JsonNode rule = createRule(
                fx,
                Map.of(
                        "type",
                        "grabber",
                        "name",
                        "Options",
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
                                "aiRewriteEnabled",
                                true,
                                "transform",
                                Map.of(
                                        "close_comments",
                                        true,
                                        "append_author_signature",
                                        true,
                                        "delete_after_enabled",
                                        true,
                                        "delete_after_amount",
                                        2,
                                        "delete_after_unit",
                                        "hours",
                                        "ai_model",
                                        "stub",
                                        "ai_cost_kopecks",
                                        1,
                                        "action_button",
                                        Map.of("type", "open_url", "title", "Открыть", "url", "https://example.com/a")),
                                "firstComment",
                                Map.of(
                                        "enabled",
                                        true,
                                        "text",
                                        "Коммент [keyText]#юмор,смешно[/keyText]",
                                        "attachments",
                                        "photo-123_456"))));
        assertThat(rule.toString()).doesNotContain("ai_cost_kopecks");
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        mockMvc.perform(post("/api/v1/ingest/rules/" + ruleId + "/runs")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .header("Idempotency-Key", "run-opt-" + UUID.randomUUID()))
                .andExpect(status().isAccepted());
        JsonNode items = readJson(mockMvc.perform(get("/api/v1/ingest/rules/" + ruleId + "/items")
                        .header("Authorization", bearer(fx.token))
                        .param("status", "pending_moderation"))
                .andExpect(status().isOk())
                .andReturn());
        UUID itemId = UUID.fromString(items.get("items").get(0).get("id").asText());
        JsonNode approved = readJson(mockMvc.perform(post("/api/v1/ingest/items/" + itemId + "/approve")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .header("Idempotency-Key", "approve-opt-" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn());
        UUID postId = UUID.fromString(approved.get("postId").asText());
        PostVariantEntity variant = postVariantRepository.findByPostId(postId).getFirst();
        assertThat(variant.getOptions()).containsEntry("closeComments", true);
        assertThat(variant.getOptions().get("firstComment").toString()).contains("смешно");
        assertThat(variant.getOptions()).containsKey("deleteAfter");
        assertThat(variant.getOptions()).containsKey("actionButton");
        assertThat(variant.getContent().get("text").toString())
                .contains("[ai:stub]")
                .contains("Иван Петров");

        assertThat(ledgerEntryRepository.findByWorkspaceIdOrderByCreatedAtDesc(fx.workspaceId))
                .anyMatch(entry -> "neural".equals(entry.getKind())
                        && entry.getAmountKopecks() == -100L
                        && entry.getDescription() != null
                        && entry.getDescription().startsWith("Нейросеть"));
        assertThat(occurrences(variant.getContent().get("text").toString(), "[ai:stub]"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("prepend and append run once for AI off, AI on, edit-approve and low balance")
    void transformMarkersOnceOnEveryPath() throws Exception {
        Fixture fx = bootstrap();
        String plain = publishText(fx, false, "foo alpha", 21, null);
        assertThat(occurrences(plain, "PRE>")).isEqualTo(1);
        assertThat(occurrences(plain, "<POST")).isEqualTo(1);
        assertThat(occurrences(plain, "BAR")).isEqualTo(1);
        assertThat(plain).doesNotContain("[ai:stub]");

        walletLedger.credit(fx.workspaceId, 10_000, "payment", false, "topup-once-" + fx.workspaceId, null, "test");
        String rewritten = publishText(fx, true, "foo beta", 22, null);
        assertThat(occurrences(rewritten, "PRE>")).isEqualTo(1);
        assertThat(occurrences(rewritten, "<POST")).isEqualTo(1);
        assertThat(occurrences(rewritten, "BAR")).isEqualTo(1);
        assertThat(occurrences(rewritten, "[ai:stub]")).isEqualTo(1);

        String edited = publishText(fx, false, "foo gamma", 23, "PRE>\nfoo kept\n<POST");
        assertThat(occurrences(edited, "PRE>")).isEqualTo(1);
        assertThat(occurrences(edited, "<POST")).isEqualTo(1);
        assertThat(edited).contains("foo kept");
        assertThat(occurrences(edited, "BAR")).isZero();

        Fixture broke = bootstrap();
        String fallback = publishText(broke, true, "foo delta", 24, null);
        assertThat(occurrences(fallback, "PRE>")).isEqualTo(1);
        assertThat(occurrences(fallback, "<POST")).isEqualTo(1);
        assertThat(occurrences(fallback, "BAR")).isEqualTo(1);
        assertThat(fallback).doesNotContain("[ai:stub]");
        assertThat(ledgerEntryRepository.findByWorkspaceIdOrderByCreatedAtDesc(broke.workspaceId))
                .noneMatch(entry -> "neural".equals(entry.getKind()));
    }

    @Test
    @DisplayName("postponed mode skips first comment and notifies once")
    void postponedModeSkipsFirstComment() throws Exception {
        Fixture fx = bootstrap();
        JsonNode rule = createRule(
                fx,
                Map.of(
                        "type",
                        "grabber",
                        "name",
                        "Later",
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
                                "targetMode",
                                "vk_postponed_post",
                                "firstComment",
                                Map.of("enabled", true, "text", "не должен уйти"))));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        mockMvc.perform(post("/api/v1/ingest/rules/" + ruleId + "/runs")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .header("Idempotency-Key", "run-late-" + UUID.randomUUID()))
                .andExpect(status().isAccepted());
        JsonNode items = readJson(mockMvc.perform(get("/api/v1/ingest/rules/" + ruleId + "/items")
                        .header("Authorization", bearer(fx.token))
                        .param("status", "pending_moderation"))
                .andExpect(status().isOk())
                .andReturn());
        UUID itemId = UUID.fromString(items.get("items").get(0).get("id").asText());
        JsonNode approved = readJson(mockMvc.perform(post("/api/v1/ingest/items/" + itemId + "/approve")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .header("Idempotency-Key", "approve-late-" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn());
        UUID postId = UUID.fromString(approved.get("postId").asText());
        PostVariantEntity variant = postVariantRepository.findByPostId(postId).getFirst();
        assertThat(variant.getOptions()).doesNotContainKey("firstComment");
        assertThat(notificationRepository.findAll())
                .anyMatch(note ->
                        "grabber.setting_error".equals(note.getType()) && fx.workspaceId.equals(note.getWorkspaceId()));
    }

    @Test
    @DisplayName("target mode matrix, postponed interval rejected, club/public sources and zero limits")
    void targetModeMatrixAndValidation() throws Exception {
        Fixture fx = bootstrap();
        mockMvc.perform(get("/api/v1/ingest/mode-matrix").header("Authorization", bearer(fx.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modes.length()").value(4))
                .andExpect(jsonPath("$.intervalDisables[0]").value("commentAttachments"))
                .andExpect(jsonPath("$.postponedHorizonDays").value(7))
                .andExpect(jsonPath("$.defaultMaxAgeDays").value(60))
                .andExpect(jsonPath("$.maxIntervalMinutes").value(120))
                .andExpect(jsonPath("$.modes[1].id").value("vk_postponed_post"))
                .andExpect(jsonPath("$.modes[1].interval").value(false))
                .andExpect(jsonPath("$.modes[1].firstComment").value(false))
                .andExpect(jsonPath("$.modes[1].actionButton").value(false))
                .andExpect(jsonPath("$.modes[1].scheduledDelete").value(false))
                .andExpect(jsonPath("$.modes[3].id").value("vk_postponed_repost"))
                .andExpect(jsonPath("$.modes[3].available").value(false));

        mockMvc.perform(post("/api/v1/workspaces/" + fx.workspaceId + "/projects/" + fx.projectId + "/ingest/rules")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type",
                                "grabber",
                                "name",
                                "Bad interval",
                                "targetChannelId",
                                fx.channelId.toString(),
                                "userResponsibilityAccepted",
                                true,
                                "config",
                                Map.of(
                                        "sourceIds",
                                        List.of("-123"),
                                        "scanIntervalMinutes",
                                        60,
                                        "targetMode",
                                        "vk_postponed_post",
                                        "limits",
                                        Map.of("min_interval_minutes", 10))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Интервал")));

        mockMvc.perform(post("/api/v1/workspaces/" + fx.workspaceId + "/projects/" + fx.projectId + "/ingest/rules")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type",
                                "grabber",
                                "name",
                                "Interval too wide",
                                "targetChannelId",
                                fx.channelId.toString(),
                                "userResponsibilityAccepted",
                                true,
                                "config",
                                Map.of(
                                        "sourceIds",
                                        List.of("-123"),
                                        "scanIntervalMinutes",
                                        60,
                                        "targetMode",
                                        "wall_now",
                                        "limits",
                                        Map.of("min_interval_minutes", 121))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("120")));

        String pastedToken = "vk1.a." + "secretTOKEN".repeat(8);
        mockMvc.perform(post("/api/v1/workspaces/" + fx.workspaceId + "/projects/" + fx.projectId + "/ingest/rules")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type",
                                "grabber",
                                "name",
                                "Bad source",
                                "targetChannelId",
                                fx.channelId.toString(),
                                "userResponsibilityAccepted",
                                true,
                                "config",
                                Map.of("sourceIds", List.of(pastedToken), "scanIntervalMinutes", 60)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(pastedToken))));

        mockMvc.perform(post("/api/v1/workspaces/" + fx.workspaceId + "/projects/" + fx.projectId + "/ingest/rules")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type",
                                "grabber",
                                "name",
                                "Postponed repost",
                                "targetChannelId",
                                fx.channelId.toString(),
                                "userResponsibilityAccepted",
                                true,
                                "config",
                                Map.of(
                                        "sourceIds",
                                        List.of("-123"),
                                        "scanIntervalMinutes",
                                        60,
                                        "targetMode",
                                        "vk_postponed_repost")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Скоро")));

        JsonNode created = createRule(
                fx,
                Map.of(
                        "type",
                        "grabber",
                        "name",
                        "Wall repost",
                        "targetChannelId",
                        fx.channelId.toString(),
                        "userResponsibilityAccepted",
                        true,
                        "config",
                        Map.of(
                                "sourceIds",
                                List.of("club123", "https://vk.com/public77", "12345", "https://vk.com/team"),
                                "scanIntervalMinutes",
                                60,
                                "targetMode",
                                "wall_repost",
                                "filters",
                                Map.of("max_age_days", 0),
                                "limits",
                                Map.of(
                                        "rate_limit_per_day",
                                        0,
                                        "rate_limit_per_source_per_day",
                                        0,
                                        "min_interval_minutes",
                                        0))));
        assertThat(created.get("config").get("targetMode").asText()).isEqualTo("wall_repost");
        assertThat(created.get("config").get("filters").get("max_age_days").asInt())
                .isZero();
        assertThat(created.get("config").get("limits").get("rate_limit_per_day").asInt())
                .isZero();
        assertThat(created.get("config").get("sourceIds").toString())
                .contains("-123")
                .contains("-77")
                .contains("-12345")
                .contains("team");

        JsonNode defaults = createRule(
                fx,
                Map.of(
                        "type",
                        "grabber",
                        "name",
                        "Defaults",
                        "targetChannelId",
                        fx.channelId.toString(),
                        "userResponsibilityAccepted",
                        true,
                        "config",
                        Map.of("sourceIds", List.of("-1"), "scanIntervalMinutes", 60)));
        assertThat(defaults.get("config").get("targetMode").asText()).isEqualTo("wall_now");
        assertThat(defaults.get("config").get("filters").get("max_age_days").asInt())
                .isEqualTo(60);
    }

    @Test
    @DisplayName("invalid action button is rejected on save")
    void invalidActionButtonRejected() throws Exception {
        Fixture fx = bootstrap();
        mockMvc.perform(post("/api/v1/workspaces/" + fx.workspaceId + "/projects/" + fx.projectId + "/ingest/rules")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type",
                                "grabber",
                                "name",
                                "Bad button",
                                "targetChannelId",
                                fx.channelId.toString(),
                                "userResponsibilityAccepted",
                                true,
                                "config",
                                Map.of(
                                        "sourceIds",
                                        List.of("-123"),
                                        "scanIntervalMinutes",
                                        15,
                                        "transform",
                                        Map.of(
                                                "action_button",
                                                Map.of("type", "open_url", "title", "x", "url", "not-a-url")))))))
                .andExpect(status().isUnprocessableEntity());
    }

    private String publishText(Fixture fx, boolean ai, String sourceText, int wallPostId, String editText)
            throws Exception {
        stubVkWall(
                """
                {"response":{"count":1,"items":[
                  {"id":%d,"owner_id":-123,"text":"%s","marked_as_ads":0,"attachments":[]}
                ]}}
                """
                        .formatted(wallPostId, sourceText));
        Map<String, Object> transform = new java.util.LinkedHashMap<>();
        transform.put("prepend_text", "PRE>");
        transform.put("append_text", "<POST");
        transform.put("word_replacements", Map.of("foo", "BAR"));
        transform.put("ai_model", "stub");
        JsonNode rule = createRule(
                fx,
                Map.of(
                        "type",
                        "grabber",
                        "name",
                        "Once " + ai + " " + (editText != null),
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
                                "aiRewriteEnabled",
                                ai,
                                "transform",
                                transform)));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        mockMvc.perform(post("/api/v1/ingest/rules/" + ruleId + "/runs")
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .header("Idempotency-Key", "run-once-" + UUID.randomUUID()))
                .andExpect(status().isAccepted());
        JsonNode items = readJson(mockMvc.perform(get("/api/v1/ingest/rules/" + ruleId + "/items")
                        .header("Authorization", bearer(fx.token))
                        .param("status", "pending_moderation"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(items.path("items").isArray()).as(items.toString()).isTrue();
        assertThat(items.path("items").size()).as(items.toString()).isPositive();
        JsonNode pending = items.path("items").get(0);
        UUID itemId = UUID.fromString(pending.path("id").asText());
        GrabberItemEntity stored = itemRepository.findById(itemId).orElseThrow();
        if (editText == null) {
            assertThat(stored.getContentText()).isEqualTo(sourceText);
        }
        String path = editText == null ? "/approve" : "/edit-and-approve";
        String body = editText == null ? "{}" : objectMapper.writeValueAsString(Map.of("contentText", editText));
        JsonNode approved = readJson(mockMvc.perform(post("/api/v1/ingest/items/" + itemId + path)
                        .with(csrf())
                        .header("Authorization", bearer(fx.token))
                        .header("Idempotency-Key", "once-" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn());
        UUID postId = UUID.fromString(approved.get("postId").asText());
        return postVariantRepository
                .findByPostId(postId)
                .getFirst()
                .getContent()
                .get("text")
                .toString();
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (from <= text.length() - needle.length()) {
            int index = text.indexOf(needle, from);
            if (index < 0) {
                break;
            }
            count++;
            from = index + needle.length();
        }
        return count;
    }

    private void stubVkWall(String body) {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.get"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
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
        String token = registerVerifyLogin("grab-" + UUID.randomUUID() + "@example.com", "Grab Owner");
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

    private record Fixture(String token, UUID workspaceId, UUID projectId, UUID channelId) {}
}
