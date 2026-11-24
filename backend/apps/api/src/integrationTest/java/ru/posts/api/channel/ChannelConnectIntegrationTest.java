package ru.posts.api.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.SocialAccountEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.SocialAccountRepository;
import ru.posts.persistence.repository.WorkspaceRepository;
import ru.posts.testsupport.emulator.SocialEmulatorProcess;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChannelConnectIntegrationTest {

    private static final Pattern TOKEN_IN_BODY = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    static SocialEmulatorProcess emulator;

    @DynamicPropertySource
    static void socialProps(DynamicPropertyRegistry registry) {
        try {
            if (emulator == null) {
                System.setProperty("posts.repo.root", findRepoRoot());
                emulator = SocialEmulatorProcess.start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (emulator != null) {
                        emulator.close();
                    }
                }));
            }
            registry.add("posts.channels.social-base-url", emulator::baseUrl);
            registry.add("posts.channels.vk-token-uri", () -> emulator.baseUrl() + "/oauth/vk/token");
            registry.add("posts.channels.vk-authorization-uri", () -> emulator.baseUrl() + "/oauth/vk/authorize");
            registry.add("posts.channels.ok-token-uri", () -> emulator.baseUrl() + "/oauth/ok/token");
            registry.add("posts.channels.ok-authorization-uri", () -> emulator.baseUrl() + "/oauth/ok/authorize");
            registry.add("posts.crypto.kek", () -> "integration-test-kek-material");
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static String findRepoRoot() {
        java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath();
        for (int i = 0; i < 8; i++) {
            if (java.nio.file.Files.isDirectory(dir.resolve("tests/social-emulator"))
                    && java.nio.file.Files.isDirectory(dir.resolve("backend"))) {
                return dir.toString();
            }
            dir = dir.getParent();
            if (dir == null) {
                break;
            }
        }
        return java.nio.file.Path.of("../..").toAbsolutePath().normalize().toString();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoggingAuthMailSender mailSender;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private WorkspaceTokenVault tokenVault;

    @BeforeAll
    void ensureEmulator() throws Exception {
        if (emulator == null) {
            emulator = SocialEmulatorProcess.start();
        }
        emulator.reset();
    }

    @AfterAll
    void stopEmulator() {
        if (emulator != null) {
            emulator.close();
            emulator = null;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        mailSender.clear();
        emulator.reset();
        emulator.post("/_control/connect/reset", "");
    }

    @Test
    @DisplayName("TC-CH-01 VK admin connect → active + encrypted tokens + audit")
    void tcCh01_vkAdminConnect() throws Exception {
        Session s = session();
        JsonNode start = readJson(mockMvc.perform(post("/api/v1/channels/connect/vk/start")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .param("workspaceId", s.workspaceId.toString())
                        .param("projectId", s.projectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").isNotEmpty())
                .andReturn());

        JsonNode page = readJson(mockMvc.perform(post("/api/v1/channels/connect/vk/callback")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "vk-admin",
                                "state", start.get("state").asText(),
                                "workspaceId", s.workspaceId.toString(),
                                "projectId", s.projectId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("active"))
                .andExpect(jsonPath("$.items[0].network").value("vk"))
                .andReturn());

        UUID channelId = UUID.fromString(page.get("items").get(0).get("id").asText());
        ChannelEntity channel = channelRepository.findById(channelId).orElseThrow();
        SocialAccountEntity account =
                socialAccountRepository.findById(channel.getSocialAccountId()).orElseThrow();
        assertThat(account.getAccessTokenEnc()).isNotNull();
        assertThat(new String(account.getAccessTokenEnc(), java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("vk-access-admin");
        assertThat(tokenVault.unseal(s.workspaceId, account.getAccessTokenEnc()))
                .isEqualTo("vk-access-admin");

        mockMvc.perform(get("/api/v1/workspaces/" + s.workspaceId + "/audit").header("Authorization", bearer(s.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.action=='channel.connected')]").exists());
    }

    @Test
    @DisplayName("TC-CH-01b VK callback resolves workspaceId from state when omitted")
    void tcCh01b_vkCallbackResolvesWorkspaceFromState() throws Exception {
        Session s = session();
        JsonNode start = readJson(mockMvc.perform(post("/api/v1/channels/connect/vk/start")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .param("workspaceId", s.workspaceId.toString()))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(post("/api/v1/channels/connect/vk/callback")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "vk-admin", "state", start.get("state").asText()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(s.workspaceId.toString()))
                .andExpect(jsonPath("$.items[0].status").value("active"));
    }

    @Test
    @DisplayName("TC-CH-02 VK no admin → insufficient_permissions")
    void tcCh02_vkNoAdmin() throws Exception {
        Session s = session();
        JsonNode start = readJson(mockMvc.perform(post("/api/v1/channels/connect/vk/start")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .param("workspaceId", s.workspaceId.toString()))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(post("/api/v1/channels/connect/vk/callback")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "vk-no-admin",
                                "state", start.get("state").asText(),
                                "workspaceId", s.workspaceId.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("insufficient_permissions"));
        assertThat(channelRepository.findByWorkspaceIdAndDeletedAtIsNull(s.workspaceId))
                .isEmpty();
    }

    @Test
    @DisplayName("TC-CH-03 / TC-CH-07 Telegram permissions and missing chat")
    void tcCh03_07_telegram() throws Exception {
        Session s = session();
        mockMvc.perform(post("/api/v1/channels/connect/tg/confirm")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", s.workspaceId.toString(),
                                "projectId", s.projectId.toString(),
                                "username", "@tg_nopost"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("insufficient_permissions"));

        mockMvc.perform(post("/api/v1/channels/connect/tg/confirm")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("workspaceId", s.workspaceId.toString(), "username", "@tg_missing"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        mockMvc.perform(post("/api/v1/channels/connect/tg/confirm")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", s.workspaceId.toString(),
                                "projectId", s.projectId.toString(),
                                "username", "@tg_brand"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("active"))
                .andExpect(jsonPath("$.items[0].network").value("tg"));
    }

    @Test
    @DisplayName("TC-CH-08 / TC-CH-09 OK OAuth connect")
    void tcCh08_09_ok() throws Exception {
        Session s = session();
        JsonNode start = readJson(mockMvc.perform(post("/api/v1/channels/connect/ok/start")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .param("workspaceId", s.workspaceId.toString()))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(post("/api/v1/channels/connect/ok/callback")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "ok-admin",
                                "state", start.get("state").asText(),
                                "workspaceId", s.workspaceId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("active"));

        Session s2 = session();
        JsonNode start2 = readJson(mockMvc.perform(post("/api/v1/channels/connect/ok/start")
                        .with(csrf())
                        .header("Authorization", bearer(s2.token))
                        .param("workspaceId", s2.workspaceId.toString()))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(post("/api/v1/channels/connect/ok/callback")
                        .with(csrf())
                        .header("Authorization", bearer(s2.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "ok-no-admin",
                                "state", start2.get("state").asText(),
                                "workspaceId", s2.workspaceId.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("insufficient_permissions"));
    }

    @Test
    @DisplayName("TC-CH-10 / TC-CH-11 Max connect")
    void tcCh10_11_max() throws Exception {
        Session s = session();
        mockMvc.perform(post("/api/v1/channels/connect/max/confirm")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("workspaceId", s.workspaceId.toString(), "username", "max_brand"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("active"));

        mockMvc.perform(post("/api/v1/channels/connect/max/confirm")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("workspaceId", s.workspaceId.toString(), "username", "max_locked"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("insufficient_permissions"));
    }

    @Test
    @DisplayName("TC-CH-05 / TC-CH-13 / TC-CH-14 / TC-CH-15 plan limit, disable, channel sets")
    void tcCh05_13_14_15_limitsAndSets() throws Exception {
        Session s = session();
        // Free plan already (3 channels)
        connectTg(s, "@tg_brand");
        // fabricate two more active channels without social calls
        seedChannel(s.workspaceId, s.projectId, "vk", "-1");
        seedChannel(s.workspaceId, s.projectId, "ok", "ok-x");

        mockMvc.perform(post("/api/v1/channels/connect/max/confirm")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("workspaceId", s.workspaceId.toString(), "username", "max_brand"))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("plan_limit_exceeded"));

        List<ChannelEntity> channels = channelRepository.findByWorkspaceIdAndDeletedAtIsNull(s.workspaceId);
        UUID firstId = channels.getFirst().getId();
        mockMvc.perform(delete("/api/v1/channels/" + firstId).with(csrf()).header("Authorization", bearer(s.token)))
                .andExpect(status().isOk());
        assertThat(channelRepository.findById(firstId).orElseThrow().getStatus())
                .isEqualTo("disabled");

        List<UUID> ids = new ArrayList<>();
        for (ChannelEntity c : channelRepository.findByWorkspaceIdAndDeletedAtIsNull(s.workspaceId)) {
            ids.add(c.getId());
        }
        mockMvc.perform(post("/api/v1/channel-sets")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .param("workspaceId", s.workspaceId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Утро", "channelIds", ids))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.channelIds.length()").value(ids.size()));

        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            tooMany.add(UUID.randomUUID());
        }
        mockMvc.perform(post("/api/v1/channel-sets")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .param("workspaceId", s.workspaceId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Too big", "channelIds", tooMany))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-CH-16 upgrade raises channel limit; networks capabilities")
    void tcCh16_upgradeAndCapabilities() throws Exception {
        Session s = session();
        seedChannel(s.workspaceId, s.projectId, "vk", "-10");
        seedChannel(s.workspaceId, s.projectId, "vk", "-11");
        seedChannel(s.workspaceId, s.projectId, "vk", "-12");

        WorkspaceEntity ws = workspaceRepository.findById(s.workspaceId).orElseThrow();
        PlanEntity start = planRepository.findByCode("start").orElseThrow();
        ws.setPlanId(start.getId());
        workspaceRepository.save(ws);

        mockMvc.perform(post("/api/v1/channels/connect/max/confirm")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("workspaceId", s.workspaceId.toString(), "username", "max_brand"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("active"));

        mockMvc.perform(get("/api/v1/networks/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.networks").isArray())
                .andExpect(jsonPath("$.networks[?(@.id=='tg')].text.maxChars").exists());
    }

    private void connectTg(Session s, String username) throws Exception {
        mockMvc.perform(post("/api/v1/channels/connect/tg/confirm")
                        .with(csrf())
                        .header("Authorization", bearer(s.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", s.workspaceId.toString(),
                                "projectId", s.projectId.toString(),
                                "username", username))))
                .andExpect(status().isOk());
    }

    private void seedChannel(UUID workspaceId, UUID projectId, String network, String externalId) {
        ChannelEntity channel = new ChannelEntity();
        channel.setWorkspaceId(workspaceId);
        channel.setProjectId(projectId);
        channel.setNetwork(network);
        channel.setExternalId(externalId);
        channel.setType("group");
        channel.setTitle(network + "-" + externalId);
        channel.setStatus("active");
        channelRepository.save(channel);
    }

    private Session session() throws Exception {
        String email = "ch-" + UUID.randomUUID() + "@example.com";
        String token = registerVerifyLogin(email, "Channel Owner");
        UUID workspaceId = firstWorkspaceId(token);
        JsonNode project = readJson(mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Main", "color", "#112233"))))
                .andExpect(status().isCreated())
                .andReturn());
        return new Session(token, workspaceId, UUID.fromString(project.get("id").asText()));
    }

    private String registerVerifyLogin(String email, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "correct-horse-battery",
                                "name", name))))
                .andExpect(status().isCreated());
        String verifyToken = mailSender.messages().stream()
                .filter(m -> m.to().equalsIgnoreCase(email))
                .reduce((a, b) -> b)
                .map(m -> extractToken(m.body()))
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
                                Map.of("email", email, "password", "correct-horse-battery"))))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(login).get("accessToken").asText();
    }

    private UUID firstWorkspaceId(String accessToken) throws Exception {
        JsonNode list = readJson(mockMvc.perform(get("/api/v1/workspaces").header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn());
        return UUID.fromString(list.get("items").get(0).get("id").asText());
    }

    private static String extractToken(String body) {
        Matcher matcher = TOKEN_IN_BODY.matcher(body == null ? "" : body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record Session(String token, UUID workspaceId, UUID projectId) {}
}
