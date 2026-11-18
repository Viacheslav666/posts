package ru.posts.api.qa13;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
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
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
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
import ru.posts.api.ord.OrdWireMockStubs;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.MediaAssetEntity;
import ru.posts.persistence.entity.OrdCreativeEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.MediaAssetRepository;
import ru.posts.persistence.repository.OrdCreativeRepository;
import ru.posts.persistence.repository.OrdInvoiceRepository;
import ru.posts.persistence.repository.OrdStatisticsRepository;
import ru.posts.persistence.repository.PublicationRepository;

/**
 * QA-13 end-to-end parity: advertising post with media → erid → marker placement ({@code
 * text_end}) → act + statistics (WireMock VK ORD; PR-CI safe).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class Qa13ParityOrdE2EIT {

    private static final Pattern TOKEN_IN_BODY = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @RegisterExtension
    static WireMockExtension vkOrd = WireMockExtension.newInstance()
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
        registry.add("posts.ord.vk.read-timeout", () -> "2s");
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
    MediaAssetRepository mediaAssetRepository;

    @Autowired
    PublicationRepository publicationRepository;

    @Autowired
    OrdCreativeRepository creativeRepository;

    @Autowired
    OrdStatisticsRepository statisticsRepository;

    @Autowired
    OrdInvoiceRepository invoiceRepository;

    @BeforeEach
    void setUp() {
        mailSender.clear();
        vkOrd.resetAll();
        OrdWireMockStubs.stubVkHappyPath(vkOrd);
        OrdWireMockStubs.stubVkCreativeSuccess(vkOrd, OrdWireMockStubs.VK_ERID);
    }

    @Test
    @DisplayName("QA-13: ad+media → erid → text_end marking → act+statistics")
    void adMediaEridPlacementActAndStats() throws Exception {
        String token = registerVerifyLogin("qa13-e2e-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");
        UUID mediaId = seedReadyJpeg(workspaceId);
        upsertConnection(token, workspaceId);

        Map<String, Object> marking = validAdMarking();
        marking.put("placement", "text_end");

        UUID postId = createAdDraftWithMedia(token, workspaceId, channel.getId(), mediaId, marking);

        mockMvc.perform(post("/api/v1/posts/" + postId + "/ord/register")
                        .with(csrf())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erid").value(OrdWireMockStubs.VK_ERID));

        Map<String, Object> previewMarking = new LinkedHashMap<>(marking);
        previewMarking.put("placement", "text_end");
        JsonNode preview = readJson(mockMvc.perform(post("/api/v1/posts/preview")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "network",
                                "vk",
                                "content",
                                Map.of("text", "Рекламный пост с медиа", "mediaIds", List.of(mediaId.toString())),
                                "ad",
                                true,
                                "erid",
                                OrdWireMockStubs.VK_ERID,
                                "adMarking",
                                previewMarking))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plainText")
                        .value(org.hamcrest.Matchers.endsWith("erid: " + OrdWireMockStubs.VK_ERID)))
                .andExpect(jsonPath("$.plainText").value(org.hamcrest.Matchers.startsWith("Рекламный пост с медиа")))
                .andReturn());
        assertThat(preview.path("plainText").asText()).contains("Реклама");

        OrdCreativeEntity creative = creativeRepository.findByPostId(postId).getFirst();
        assertThat(creative.getErid()).isEqualTo(OrdWireMockStubs.VK_ERID);

        seedSucceededPublication(workspaceId, postId, channel.getId(), "qa13-1001");
        YearMonth period = YearMonth.from(Instant.now().atZone(MOSCOW)).minusMonths(1);
        creative.setDisplayStartedAt(period.atDay(1).atStartOfDay(MOSCOW).toInstant());
        creative.setDisplayEndedAt(null);
        creativeRepository.save(creative);

        JsonNode report = readJson(mockMvc.perform(post("/api/v1/ord/reports/run")
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
        assertThat(report.path("statisticsCount").asInt()).isGreaterThanOrEqualTo(1);

        assertThat(statisticsRepository.findAll()).isNotEmpty();
        assertThat(invoiceRepository
                        .findByWorkspaceIdAndPeriodYearAndPeriodMonth(
                                workspaceId, period.getYear(), period.getMonthValue())
                        .stream()
                        .anyMatch(i -> i.getDeletedAt() == null))
                .isTrue();

        vkOrd.verify(putRequestedFor(urlPathMatching("/v3/creative/.*")));
        vkOrd.verify(putRequestedFor(urlPathMatching("/v1/invoice/.*")));
        vkOrd.verify(postRequestedFor(urlPathEqualTo("/v1/statistics")));
    }

    private UUID seedReadyJpeg(UUID workspaceId) {
        MediaAssetEntity asset = new MediaAssetEntity();
        asset.setId(UuidV7.generate());
        asset.setWorkspaceId(workspaceId);
        asset.setKind("image");
        asset.setMime("image/jpeg");
        asset.setFilename("ad-qa13.jpg");
        asset.setS3Key("workspaces/" + workspaceId + "/media/" + asset.getId() + "/ad-qa13.jpg");
        asset.setSizeBytes(22L);
        asset.setWidth(1080);
        asset.setHeight(1920);
        asset.setSha256("qa13-media-sha");
        asset.setStatus("ready");
        asset.setCreatedAt(Instant.now());
        asset.setUpdatedAt(Instant.now());
        return mediaAssetRepository.save(asset).getId();
    }

    private UUID createAdDraftWithMedia(
            String token, UUID workspaceId, UUID channelId, UUID mediaId, Map<String, Object> adMarking)
            throws Exception {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("text", "Рекламный пост с медиа");
        content.put("mediaIds", List.of(mediaId.toString()));
        JsonNode created = readJson(mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "title",
                                "QA-13 ad media",
                                "baseContent",
                                content,
                                "ad",
                                true,
                                "adMarking",
                                adMarking,
                                "variants",
                                List.of(Map.of(
                                        "channelId", channelId.toString(), "network", "vk", "content", content))))))
                .andExpect(status().isCreated())
                .andReturn());
        return UUID.fromString(created.path("id").asText());
    }

    private void upsertConnection(String token, UUID workspaceId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("apiKey", "vk-test-key");
        body.put("publisherInn", "7707083893");
        body.put("publisherName", "Publisher");
        body.put("orgType", "juridical");
        body.put("address", "Москва, ул. Тверская, 1");
        body.put("responsibilityAccepted", true);
        body.put("isDefault", true);
        mockMvc.perform(put("/api/v1/workspaces/" + workspaceId + "/ord/connections/vk_ord")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/ord/connections/vk_ord/test")
                        .with(csrf())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
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
        pub.setIdempotencyKey("qa13-" + remotePostId + "-" + UUID.randomUUID());
        pub.setState("succeeded");
        pub.setRemotePostId(remotePostId);
        pub.setPublishedAt(Instant.now().minusSeconds(86_400));
        pub.setCreatedAt(Instant.now());
        publicationRepository.save(pub);
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
