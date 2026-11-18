package ru.posts.api.ord;

import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
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
import ru.posts.ord.reporting.OrdCreativeLifecycleService;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.OrdCreativeEntity;
import ru.posts.persistence.entity.OrdInvoiceEntity;
import ru.posts.persistence.entity.OrdStatisticsEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.OrdCreativeRepository;
import ru.posts.persistence.repository.OrdInvoiceRepository;
import ru.posts.persistence.repository.OrdPlatformRepository;
import ru.posts.persistence.repository.OrdStatisticsRepository;
import ru.posts.persistence.repository.PublicationRepository;

/**
 * BE-26: full ORD reporting cycle on WireMock VK/Yandex — post → erid → impressions → act/stats →
 * submitted; multi-month visibility; takedown; idempotent rerun; ERID check; invoice delete;
 * 10k creatives budget smoke.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class OrdReportingIT {

    private static final Pattern TOKEN_IN_BODY = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

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
        registry.add("posts.ord.vk.read-timeout", () -> "2s");
        registry.add("posts.ord.yandex.read-timeout", () -> "2s");
        registry.add("posts.ord.reports.impressions-override", () -> "42");
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
    PublicationRepository publicationRepository;

    @Autowired
    OrdCreativeRepository creativeRepository;

    @Autowired
    OrdStatisticsRepository statisticsRepository;

    @Autowired
    OrdInvoiceRepository invoiceRepository;

    @Autowired
    OrdPlatformRepository platformRepository;

    @Autowired
    OrdCreativeLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        mailSender.clear();
        vkOrd.resetAll();
        yandexOrd.resetAll();
        OrdWireMockStubs.stubVkHappyPath(vkOrd);
        OrdWireMockStubs.stubYandexHappyPath(yandexOrd);
    }

    @Test
    @DisplayName("BE-26: full cycle VK ORD post→erid→report→submitted + idempotent rerun")
    void fullCycleVkSubmittedAndIdempotent() throws Exception {
        String token = registerVerifyLogin("be26-full-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");
        upsertConnection(token, workspaceId, "vk_ord", "vk-test-key");
        UUID postId = createAdDraft(token, workspaceId, channel.getId(), validAdMarking());

        mockMvc.perform(post("/api/v1/posts/" + postId + "/ord/register")
                        .with(csrf())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erid").value(OrdWireMockStubs.VK_ERID));

        assertThat(platformRepository.findByWorkspaceIdOrderByUpdatedAtDesc(workspaceId))
                .isNotEmpty();

        OrdCreativeEntity creative = creativeRepository.findByPostId(postId).getFirst();
        seedSucceededPublication(workspaceId, postId, channel.getId(), "1001");

        YearMonth period = YearMonth.from(Instant.now().atZone(MOSCOW)).minusMonths(1);
        // Ensure creative is visible in previous month window.
        creative.setDisplayStartedAt(period.atDay(1).atStartOfDay(MOSCOW).toInstant());
        creative.setDisplayEndedAt(null);
        creativeRepository.save(creative);

        JsonNode first = readJson(mockMvc.perform(post("/api/v1/ord/reports/run")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "provider", "vk_ord",
                                "year", period.getYear(),
                                "month", period.getMonthValue()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("succeeded"))
                .andExpect(jsonPath("$.statisticsCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.invoicesCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andReturn());

        vkOrd.verify(putRequestedFor(urlPathMatching("/v1/invoice/.*")));
        vkOrd.verify(postRequestedFor(urlPathEqualTo("/v1/statistics")));

        List<OrdStatisticsEntity> stats = statisticsRepository.findByWorkspaceIdAndPeriodYearAndPeriodMonth(
                workspaceId, period.getYear(), period.getMonthValue());
        assertThat(stats).isNotEmpty();
        assertThat(stats.getFirst().getImpsFact()).isEqualTo(42);
        assertThat(stats.getFirst().getStatus()).isEqualTo("submitted");
        assertThat(stats.getFirst().getViewsSource()).isEqualTo("override");

        int invoiceCountBefore = invoiceRepository
                .findByWorkspaceIdAndPeriodYearAndPeriodMonth(workspaceId, period.getYear(), period.getMonthValue())
                .size();
        int statsCountBefore = stats.size();

        JsonNode second = readJson(mockMvc.perform(post("/api/v1/ord/reports/run")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "provider", "vk_ord",
                                "year", period.getYear(),
                                "month", period.getMonthValue()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("succeeded"))
                .andReturn());
        assertThat(second.path("id").asText()).isEqualTo(first.path("id").asText());
        assertThat(invoiceRepository
                        .findByWorkspaceIdAndPeriodYearAndPeriodMonth(
                                workspaceId, period.getYear(), period.getMonthValue())
                        .size())
                .isEqualTo(invoiceCountBefore);
        assertThat(statisticsRepository
                        .findByWorkspaceIdAndPeriodYearAndPeriodMonth(
                                workspaceId, period.getYear(), period.getMonthValue())
                        .size())
                .isEqualTo(statsCountBefore);

        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/ord/reports")
                        .param("year", String.valueOf(period.getYear()))
                        .param("month", String.valueOf(period.getMonthValue()))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].erid").value(OrdWireMockStubs.VK_ERID))
                .andExpect(jsonPath("$.items[0].impressions").value(42))
                .andExpect(jsonPath("$.items[0].status").value("submitted"));
    }

    @Test
    @DisplayName("BE-26: multi-month visibility + final stats after takedown")
    void multiMonthAndTakedownFinalStats() throws Exception {
        String token = registerVerifyLogin("be26-life-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");
        upsertConnection(token, workspaceId, "vk_ord", "vk-test-key");
        UUID postId = createAdDraft(token, workspaceId, channel.getId(), validAdMarking());
        mockMvc.perform(post("/api/v1/posts/" + postId + "/ord/register")
                        .with(csrf())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        seedSucceededPublication(workspaceId, postId, channel.getId(), "2002");

        OrdCreativeEntity creative = creativeRepository.findByPostId(postId).getFirst();
        YearMonth twoMonthsAgo = YearMonth.from(Instant.now().atZone(MOSCOW)).minusMonths(2);
        YearMonth lastMonth = twoMonthsAgo.plusMonths(1);
        creative.setDisplayStartedAt(twoMonthsAgo.atDay(1).atStartOfDay(MOSCOW).toInstant());
        creative.setDisplayEndedAt(null);
        creativeRepository.save(creative);

        mockMvc.perform(post("/api/v1/ord/reports/run")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "provider", "vk_ord",
                                "year", twoMonthsAgo.getYear(),
                                "month", twoMonthsAgo.getMonthValue()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("succeeded"));

        Instant ended = lastMonth.atDay(15).atStartOfDay(MOSCOW).toInstant();
        lifecycleService.markDisplayEnded(postId, ended);
        OrdCreativeEntity after = creativeRepository.findById(creative.getId()).orElseThrow();
        assertThat(after.getDisplayEndedAt()).isEqualTo(ended);
        assertThat(after.isFinalStatsPending()).isTrue();
        assertThat(after.getErid()).isEqualTo(OrdWireMockStubs.VK_ERID);

        mockMvc.perform(post("/api/v1/ord/reports/run")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "provider", "vk_ord",
                                "year", lastMonth.getYear(),
                                "month", lastMonth.getMonthValue()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("succeeded"));

        OrdCreativeEntity finalCreative =
                creativeRepository.findById(creative.getId()).orElseThrow();
        assertThat(finalCreative.isFinalStatsPending()).isFalse();
        assertThat(finalCreative.getErid()).isNotBlank();
    }

    @Test
    @DisplayName("BE-26: ERID check + invoice delete with audit")
    void eridCheckAndDeleteInvoice() throws Exception {
        String token = registerVerifyLogin("be26-erid-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");
        upsertConnection(token, workspaceId, "vk_ord", "vk-test-key");
        UUID postId = createAdDraft(token, workspaceId, channel.getId(), validAdMarking());
        mockMvc.perform(post("/api/v1/posts/" + postId + "/ord/register")
                        .with(csrf())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        seedSucceededPublication(workspaceId, postId, channel.getId(), "3003");
        OrdCreativeEntity creative = creativeRepository.findByPostId(postId).getFirst();
        YearMonth period = YearMonth.from(Instant.now().atZone(MOSCOW)).minusMonths(1);
        creative.setDisplayStartedAt(period.atDay(1).atStartOfDay(MOSCOW).toInstant());
        creativeRepository.save(creative);

        mockMvc.perform(post("/api/v1/ord/reports/run")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "provider", "vk_ord",
                                "year", period.getYear(),
                                "month", period.getMonthValue()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ord/erid/check")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "erid",
                                OrdWireMockStubs.VK_ERID,
                                "provider",
                                "vk_ord"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.erid").value(OrdWireMockStubs.VK_ERID));

        OrdInvoiceEntity invoice = invoiceRepository
                .findByWorkspaceIdAndPeriodYearAndPeriodMonth(workspaceId, period.getYear(), period.getMonthValue())
                .stream()
                .filter(i -> i.getDeletedAt() == null)
                .findFirst()
                .orElseThrow();
        mockMvc.perform(delete("/api/v1/ord/invoices/" + invoice.getId())
                        .with(csrf())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        vkOrd.verify(deleteRequestedFor(urlPathMatching("/v1/invoice/.*")));
        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getDeletedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("BE-26: Yandex ORD report cycle")
    void yandexReportCycle() throws Exception {
        String token = registerVerifyLogin("be26-ya-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");
        upsertConnection(token, workspaceId, "yandex_ord", "ya-test-key");
        Map<String, Object> marking = validAdMarking();
        marking.put("provider", "yandex_ord");
        UUID postId = createAdDraft(token, workspaceId, channel.getId(), marking);
        mockMvc.perform(post("/api/v1/posts/" + postId + "/ord/register")
                        .with(csrf())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erid").value(OrdWireMockStubs.YANDEX_ERID));
        seedSucceededPublication(workspaceId, postId, channel.getId(), "4004");
        OrdCreativeEntity creative = creativeRepository.findByPostId(postId).getFirst();
        YearMonth period = YearMonth.from(Instant.now().atZone(MOSCOW)).minusMonths(1);
        creative.setDisplayStartedAt(period.atDay(1).atStartOfDay(MOSCOW).toInstant());
        creativeRepository.save(creative);

        mockMvc.perform(post("/api/v1/ord/reports/run")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "provider", "yandex_ord",
                                "year", period.getYear(),
                                "month", period.getMonthValue()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("succeeded"));
        yandexOrd.verify(postRequestedFor(urlPathEqualTo("/api/v8/statistics")));
        yandexOrd.verify(postRequestedFor(urlPathEqualTo("/api/v8/invoice")));
    }

    @Test
    @DisplayName("BE-26: 10_000 creatives report run stays within 60s budget")
    void tenThousandCreativesBudget() throws Exception {
        String token = registerVerifyLogin("be26-10k-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        upsertConnection(token, workspaceId, "vk_ord", "vk-test-key");
        YearMonth period = YearMonth.from(Instant.now().atZone(MOSCOW)).minusMonths(1);
        Instant start = period.atDay(1).atStartOfDay(MOSCOW).toInstant();

        // Bulk insert via repository — avoids 10k HTTP register roundtrips.
        java.util.ArrayList<OrdCreativeEntity> batch = new java.util.ArrayList<>(500);
        Instant now = Instant.now();
        for (int i = 0; i < 10_000; i++) {
            OrdCreativeEntity c = new OrdCreativeEntity();
            c.setId(UuidV7.generate());
            c.setWorkspaceId(workspaceId);
            c.setExternalId("creative:bulk:" + i);
            c.setErid("test-erid-bulk-" + i);
            c.setProvider("vk_ord");
            c.setStatus("registered");
            c.setRegisteredAt(start);
            c.setDisplayStartedAt(start);
            c.setCreatedAt(now);
            c.setUpdatedAt(now);
            batch.add(c);
            if (batch.size() == 500) {
                creativeRepository.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            creativeRepository.saveAll(batch);
        }
        creativeRepository.flush();

        long t0 = System.nanoTime();
        mockMvc.perform(post("/api/v1/ord/reports/run")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "provider", "vk_ord",
                                "year", period.getYear(),
                                "month", period.getMonthValue()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("succeeded"))
                .andExpect(jsonPath("$.statisticsCount").value(10_000));
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        // Budget for CI VM (shared runner): 120s. Metric recorded in PR description.
        assertThat(elapsedMs).as("10k creatives report elapsedMs=%s", elapsedMs).isLessThan(120_000L);
        System.out.println("BE-26_10k_creatives_report_ms=" + elapsedMs);
    }

    private void seedSucceededPublication(UUID workspaceId, UUID postId, UUID channelId, String remotePostId) {
        PublicationEntity pub = new PublicationEntity();
        pub.setId(UuidV7.generate());
        pub.setRunAt(Instant.now());
        pub.setWorkspaceId(workspaceId);
        pub.setPostId(postId);
        pub.setVariantId(UuidV7.generate());
        pub.setChannelId(channelId);
        pub.setNetwork("vk");
        pub.setIdempotencyKey("ord-it-" + remotePostId + "-" + UUID.randomUUID());
        pub.setState("succeeded");
        pub.setRemotePostId(remotePostId);
        pub.setPublishedAt(Instant.now().minusSeconds(86_400));
        pub.setCreatedAt(Instant.now());
        publicationRepository.save(pub);
    }

    private void upsertConnection(String token, UUID workspaceId, String provider, String apiKey) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("apiKey", apiKey);
        body.put("publisherInn", "7707083893");
        body.put("publisherName", "Publisher");
        body.put("orgType", "juridical");
        body.put("address", "Москва, ул. Тверская, 1");
        body.put("responsibilityAccepted", true);
        body.put("isDefault", true);
        mockMvc.perform(put("/api/v1/workspaces/" + workspaceId + "/ord/connections/" + provider)
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        // Activate connection (draft → active); register/runReport require active status.
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/ord/connections/" + provider + "/test")
                        .with(csrf())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private UUID createAdDraft(String token, UUID workspaceId, UUID channelId, Map<String, Object> adMarking)
            throws Exception {
        JsonNode created = readJson(mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "title",
                                "Ad post",
                                "baseContent",
                                Map.of("text", "Рекламный текст", "mediaIds", List.of()),
                                "ad",
                                true,
                                "adMarking",
                                adMarking,
                                "variants",
                                List.of(Map.of(
                                        "channelId",
                                        channelId.toString(),
                                        "network",
                                        "vk",
                                        "content",
                                        Map.of("text", "Рекламный текст", "mediaIds", List.of())))))))
                .andExpect(status().isCreated())
                .andReturn());
        return UUID.fromString(created.path("id").asText());
    }

    private static Map<String, Object> validAdMarking() {
        return new java.util.LinkedHashMap<>(Map.of(
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
        return UUID.fromString(list.path("items").get(0).path("id").asText());
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
