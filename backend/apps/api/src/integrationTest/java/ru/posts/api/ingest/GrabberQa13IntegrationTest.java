package ru.posts.api.ingest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import ru.posts.grabber.GrabberRunProcessor;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.GrabberItemEntity;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.GrabberItemRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.WorkspaceRepository;

/**
 * QA-13: grabber filters + near-duplicate (SimHash) regression on top of BE-27.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class GrabberQa13IntegrationTest {

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
    PlanRepository planRepository;

    @Autowired
    WorkspaceRepository workspaceRepository;

    @Autowired
    GrabberRunProcessor runProcessor;

    @BeforeEach
    void setUp() {
        mailSender.clear();
        wireMock.resetAll();
    }

    @Test
    @DisplayName("QA-13: stop_words filter → filtered_out")
    void stopWordsFilter() throws Exception {
        Fixture fx = bootstrap();
        stubVkWall(
                """
                {"response":{"count":1,"items":[
                  {"id":101,"owner_id":-123,"text":"Большой РОЗЫГРЫШ призов сегодня","marked_as_ads":0,"attachments":[]}
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
                        Map.of("exclude_ads", false, "exclude_reposts", false, "stop_words", List.of("розыгрыш"))));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        runProcessor.process(ruleId, null, "manual", "qa13-stop");
        GrabberItemEntity item =
                itemRepository.findByRuleIdOrderByDetectedAtDesc(ruleId).getFirst();
        assertThat(item.getStatus()).isEqualTo("filtered_out");
        assertThat(item.getSkipReason()).isEqualTo("stop_words");
    }

    @Test
    @DisplayName("QA-13: similar near-duplicate → suppressed_dedup / similarity")
    void similarityNearDuplicateSuppressed() throws Exception {
        Fixture fx = bootstrap();
        stubVkWall(
                """
                {"response":{"count":2,"items":[
                  {"id":201,"owner_id":-123,"text":"Сегодня в Москве прошёл сильный дождь, улицы затопило за час.","marked_as_ads":0,"attachments":[]},
                  {"id":202,"owner_id":-123,"text":"Сегодня в Москве прошёл сильный дождь, улицы затопило за час!","marked_as_ads":0,"attachments":[]}
                ]}}
                """);
        JsonNode rule = createActiveRule(
                fx,
                Map.of(
                        "sourceIds",
                        List.of("-123"),
                        "scanIntervalMinutes",
                        15,
                        "reliability",
                        Map.of(
                                "dedup_enabled",
                                true,
                                "similarity_dedup_enabled",
                                true,
                                "similarity_threshold",
                                0.8,
                                "similarity_lookback",
                                15)));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        runProcessor.process(ruleId, null, "manual", "qa13-sim");

        List<GrabberItemEntity> items = itemRepository.findByRuleIdOrderByDetectedAtDesc(ruleId);
        assertThat(items).hasSize(2);
        long pendingOrQueued = items.stream()
                .filter(i -> !"suppressed_dedup".equals(i.getStatus()))
                .count();
        long similar = items.stream()
                .filter(i -> "suppressed_dedup".equals(i.getStatus()) && "similarity".equals(i.getSkipReason()))
                .count();
        assertThat(pendingOrQueued).isEqualTo(1);
        assertThat(similar).isEqualTo(1);
    }

    @Test
    @DisplayName("QA-13: distant texts both pass similarity filter")
    void distantTextsNotSuppressedBySimilarity() throws Exception {
        Fixture fx = bootstrap();
        stubVkWall(
                """
                {"response":{"count":2,"items":[
                  {"id":301,"owner_id":-123,"text":"Рецепт борща: свёкла, капуста, картофель и сметана.","marked_as_ads":0,"attachments":[]},
                  {"id":302,"owner_id":-123,"text":"Курс доллара обновил максимум на торгах Московской биржи.","marked_as_ads":0,"attachments":[]}
                ]}}
                """);
        JsonNode rule = createActiveRule(
                fx,
                Map.of(
                        "sourceIds",
                        List.of("-123"),
                        "scanIntervalMinutes",
                        15,
                        "reliability",
                        Map.of("dedup_enabled", true, "similarity_dedup_enabled", true, "similarity_threshold", 0.8)));
        UUID ruleId = UUID.fromString(rule.get("id").asText());
        runProcessor.process(ruleId, null, "manual", "qa13-distant");
        List<GrabberItemEntity> items = itemRepository.findByRuleIdOrderByDetectedAtDesc(ruleId);
        assertThat(items).hasSize(2);
        assertThat(items.stream().noneMatch(i -> "similarity".equals(i.getSkipReason())))
                .isTrue();
    }

    private void stubVkWall(String body) {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.get"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private JsonNode createActiveRule(Fixture fx, Map<String, Object> config) throws Exception {
        return readJson(mockMvc.perform(
                        post("/api/v1/workspaces/" + fx.workspaceId + "/projects/" + fx.projectId + "/ingest/rules")
                                .with(csrf())
                                .header("Authorization", bearer(fx.token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "type",
                                        "grabber",
                                        "name",
                                        "QA13 " + UUID.randomUUID(),
                                        "targetChannelId",
                                        fx.channelId.toString(),
                                        "userResponsibilityAccepted",
                                        true,
                                        "activate",
                                        true,
                                        "config",
                                        config))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private Fixture bootstrap() throws Exception {
        String token = registerVerifyLogin("grab-qa13-" + UUID.randomUUID() + "@example.com", "Grab Owner");
        UUID workspaceId = firstWorkspaceId(token);
        PlanEntity agency = planRepository.findByCode("agency").orElseThrow();
        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        workspace.setPlanId(agency.getId());
        workspaceRepository.save(workspace);
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

    private String registerVerifyLogin(String email, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", PASSWORD, "name", name))))
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
        JsonNode login = readJson(mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn());
        return login.get("accessToken").asText();
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

    private record Fixture(String token, UUID workspaceId, UUID projectId, UUID channelId) {}
}
