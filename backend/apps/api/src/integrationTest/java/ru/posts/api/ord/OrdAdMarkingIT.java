package ru.posts.api.ord;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
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
import ru.posts.persistence.repository.ChannelRepository;

/**
 * TC-AD-* automation for BE-15 (ORD / ad marking). Uses WireMock for VK ORD + Yandex ORD.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class OrdAdMarkingIT {

    private static final Pattern TOKEN_IN_BODY = Pattern.compile("token=([A-Za-z0-9_-]+)");

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
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LoggingAuthMailSender mailSender;

    @Autowired
    ChannelRepository channelRepository;

    @BeforeEach
    void setUp() {
        mailSender.clear();
        vkOrd.resetAll();
        yandexOrd.resetAll();
        stubVkHappyPath();
        stubYandexHappyPath();
    }

    @Test
    @DisplayName("TC-AD-03: schedule isAd without ORD connection → ord_not_configured")
    void scheduleAdWithoutOrdBlocked() throws Exception {
        String token = registerVerifyLogin("ad-03-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");
        UUID postId = createAdDraft(token, workspaceId, channel.getId(), validAdMarking());

        mockMvc.perform(post("/api/v1/posts/" + postId + "/schedule")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduledAt",
                                Instant.parse("2026-12-01T12:00:00Z").toString(),
                                "version",
                                0))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ord_not_configured"));
    }

    @Test
    @DisplayName("TC-AD-04/13: register creative → erid; repeat is idempotent")
    void registerCreativeIdempotent() throws Exception {
        String token = registerVerifyLogin("ad-04-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");
        upsertConnection(token, workspaceId, "vk_ord", "vk-test-key");
        UUID postId = createAdDraft(token, workspaceId, channel.getId(), validAdMarking());

        JsonNode first = readJson(mockMvc.perform(post("/api/v1/posts/" + postId + "/ord/register")
                        .with(csrf())
                        .header("Authorization", bearer(token)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.erid").value("VkEridTest1"))
                .andExpect(jsonPath("$.status").value("registered"))
                .andReturn());

        JsonNode second = readJson(mockMvc.perform(post("/api/v1/posts/" + postId + "/ord/register")
                        .with(csrf())
                        .header("Authorization", bearer(token)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.erid").value("VkEridTest1"))
                .andReturn());

        assertThat(second.path("id").asText()).isEqualTo(first.path("id").asText());
        vkOrd.verify(1, putRequestedFor(urlPathMatching("/v3/creative/.*")));
    }

    @Test
    @DisplayName("TC-AD-11: invalid INN → ad_inn_invalid")
    void invalidInnRejected() throws Exception {
        String token = registerVerifyLogin("ad-11-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        upsertConnection(token, workspaceId, "vk_ord", "vk-test-key");

        Map<String, Object> bad = validAdMarking();
        bad.put("inn", "123");

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/ord/contractors")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "inn", "123",
                                "name", "Bad",
                                "orgType", "juridical",
                                "kpp", "770101001"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ad_inn_invalid"));
    }

    @Test
    @DisplayName("TC-AD-12: ORD 5xx on register → schedule fails closed")
    void ord5xxFailClosedOnSchedule() throws Exception {
        vkOrd.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(urlPathMatching("/v3/creative/.*"))
                .willReturn(aResponse().withStatus(503).withBody("{\"error\":\"unavailable\"}")));

        String token = registerVerifyLogin("ad-12-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");
        upsertConnection(token, workspaceId, "vk_ord", "vk-test-key");
        UUID postId = createAdDraft(token, workspaceId, channel.getId(), validAdMarking());

        mockMvc.perform(post("/api/v1/posts/" + postId + "/schedule")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduledAt",
                                Instant.parse("2026-12-02T12:00:00Z").toString(),
                                "version",
                                0))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ord_provider_unavailable"));
    }

    @Test
    @DisplayName("TC-AD-05: report run is idempotent")
    void reportRunIdempotent() throws Exception {
        String token = registerVerifyLogin("ad-05-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        upsertConnection(token, workspaceId, "vk_ord", "vk-test-key");

        JsonNode first = readJson(mockMvc.perform(post("/api/v1/ord/reports/run")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "provider",
                                "vk_ord",
                                "year",
                                2026,
                                "month",
                                10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("succeeded"))
                .andReturn());

        JsonNode second = readJson(mockMvc.perform(post("/api/v1/ord/reports/run")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId",
                                workspaceId.toString(),
                                "provider",
                                "vk_ord",
                                "year",
                                2026,
                                "month",
                                10))))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(second.path("id").asText()).isEqualTo(first.path("id").asText());
    }

    @Test
    @DisplayName("TC-AD-16: KKTU/OKSM dictionaries")
    void dictionariesAvailable() throws Exception {
        String token = registerVerifyLogin("ad-16-" + UUID.randomUUID() + "@example.com", "Owner");
        mockMvc.perform(get("/api/v1/ord/dictionaries/kktu").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").exists());
        mockMvc.perform(get("/api/v1/ord/dictionaries/oksm").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").exists());
    }

    @Test
    @DisplayName("TC-AD-17: donut/co-authors/poll saved in options")
    void postOptionsParity() throws Exception {
        String token = registerVerifyLogin("ad-17-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");

        JsonNode created = readJson(mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "title", "Options",
                                "baseContent", Map.of("text", "poll post", "mediaIds", List.of()),
                                "ad", false,
                                "variants",
                                        List.of(Map.of(
                                                "channelId",
                                                channel.getId().toString(),
                                                "network",
                                                "vk",
                                                "content",
                                                Map.of("text", "poll post", "mediaIds", List.of()),
                                                "options",
                                                Map.of(
                                                        "donutDays",
                                                        3,
                                                        "coOwnerIds",
                                                        List.of("-123", "-456"),
                                                        "poll",
                                                        Map.of(
                                                                "question",
                                                                "Q?",
                                                                "answers",
                                                                List.of("A", "B", "C")))))))))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(created.path("variants")
                        .get(0)
                        .path("options")
                        .path("donutDays")
                        .asInt())
                .isEqualTo(3);
        assertThat(created.path("variants").get(0).path("options").path("coOwnerIds"))
                .hasSize(2);
        assertThat(created.path("variants").get(0).path("options").path("poll").path("answers"))
                .hasSize(3);
    }

    @Test
    @DisplayName("TC-AD-18: IDOR foreign creative → 404")
    void foreignCreativeIdor() throws Exception {
        String ownerA = registerVerifyLogin("ad-18a-" + UUID.randomUUID() + "@example.com", "Owner A");
        UUID wsA = firstWorkspaceId(ownerA);
        ChannelEntity channel = seedChannel(wsA, "vk");
        upsertConnection(ownerA, wsA, "vk_ord", "vk-test-key");
        UUID postId = createAdDraft(ownerA, wsA, channel.getId(), validAdMarking());
        mockMvc.perform(post("/api/v1/posts/" + postId + "/ord/register")
                        .with(csrf())
                        .header("Authorization", bearer(ownerA)))
                .andExpect(status().isOk());

        String ownerB = registerVerifyLogin("ad-18b-" + UUID.randomUUID() + "@example.com", "Owner B");
        mockMvc.perform(get("/api/v1/posts/" + postId + "/ord/status").header("Authorization", bearer(ownerB)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-AD-10: preview includes marker when erid provided")
    void previewShowsMarker() throws Exception {
        String token = registerVerifyLogin("ad-10-" + UUID.randomUUID() + "@example.com", "Owner");
        mockMvc.perform(post("/api/v1/posts/preview")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "network",
                                "tg",
                                "content",
                                Map.of("text", "Body", "mediaIds", List.of()),
                                "ad",
                                true,
                                "erid",
                                "PreviewErid",
                                "adMarking",
                                Map.of(
                                        "advertiserName",
                                        "ИП Тест",
                                        "inn",
                                        "500100732259",
                                        "orgType",
                                        "ip",
                                        "kktuCodes",
                                        List.of("1.1.1"),
                                        "amount",
                                        100,
                                        "roles",
                                        List.of("advertiser"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plainText").value(org.hamcrest.Matchers.containsString("erid: PreviewErid")))
                .andExpect(jsonPath("$.plainText").value(org.hamcrest.Matchers.containsString("Реклама")));
    }

    @Test
    @DisplayName("TC-AD-15: Yandex ORD connection test works")
    void yandexConnectionTest() throws Exception {
        String token = registerVerifyLogin("ad-15-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        upsertConnection(token, workspaceId, "yandex_ord", "ya-test-key");
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/ord/connections/yandex_ord/test")
                        .with(csrf())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        yandexOrd.verify(getRequestedFor(urlPathEqualTo("/api/v8/organization"))
                .withHeader("Authorization", equalTo("Bearer ya-test-key")));
    }

    private void stubVkHappyPath() {
        vkOrd.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/v1/person/my"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"name\":\"Publisher\"}")));
        vkOrd.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(urlPathMatching("/v1/person/.*"))
                .willReturn(aResponse().withStatus(201)));
        vkOrd.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(urlPathMatching("/v1/contract/.*"))
                .willReturn(aResponse().withStatus(201)));
        vkOrd.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(urlPathMatching("/v3/creative/.*"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"erid\":\"VkEridTest1\",\"marker\":\"VkEridTest1\"}")));
        vkOrd.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(urlPathMatching("/v1/invoice/.*"))
                .willReturn(aResponse().withStatus(201)));
        vkOrd.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/v1/statistics"))
                .willReturn(aResponse().withStatus(200)));
    }

    private void stubYandexHappyPath() {
        yandexOrd.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/api/v8/organization"))
                .willReturn(aResponse().withStatus(404).withBody("{\"detail\":\"not found\"}")));
        yandexOrd.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v8/organization"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
        yandexOrd.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v8/contract"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
        yandexOrd.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v8/creative"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"YaEridTest1\",\"status\":\"Request accepted by ORD\"}")));
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
