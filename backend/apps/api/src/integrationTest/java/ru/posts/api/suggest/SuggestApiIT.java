package ru.posts.api.suggest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.SuggestItemEntity;
import ru.posts.persistence.entity.SuggestSettingsEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.SuggestItemRepository;
import ru.posts.persistence.repository.SuggestSettingsRepository;
import ru.posts.persistence.repository.WorkspaceRepository;

/**
 * BE-34 Suggest HTTP API: IDOR (foreign workspace → 404) and RBAC (Author/Viewer cannot moderate →
 * 403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class SuggestApiIT {

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
    PlanRepository planRepository;

    @Autowired
    WorkspaceRepository workspaceRepository;

    @Autowired
    ChannelRepository channelRepository;

    @Autowired
    SuggestSettingsRepository settingsRepository;

    @Autowired
    SuggestItemRepository itemRepository;

    @BeforeEach
    void setUp() {
        mailSender.clear();
    }

    @Test
    @DisplayName("IDOR: foreign workspace suggest settings/items → 404")
    void foreignWorkspaceReturns404() throws Exception {
        Fixture ownerA = seedOwnerWithChannel("idor-a");
        Fixture ownerB = seedOwnerWithChannel("idor-b");

        SuggestSettingsEntity settings = seedSettings(ownerA);
        SuggestItemEntity item = seedPendingItem(ownerA, settings);

        mockMvc.perform(get("/api/v1/suggest/settings/" + settings.getId())
                        .header("Authorization", bearer(ownerB.token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));

        mockMvc.perform(get("/api/v1/suggest/items/" + item.getId()).header("Authorization", bearer(ownerB.token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));

        mockMvc.perform(post("/api/v1/suggest/items/" + item.getId() + "/approve")
                        .with(csrf())
                        .header("Authorization", bearer(ownerB.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("RBAC: Author and Viewer cannot moderate suggest items → 403")
    void authorAndViewerCannotModerate() throws Exception {
        Fixture owner = seedOwnerWithChannel("rbac-owner");
        upgradeToAgency(owner.workspaceId);

        String authorEmail = "author-" + UUID.randomUUID() + "@example.com";
        String viewerEmail = "viewer-" + UUID.randomUUID() + "@example.com";
        registerVerifyLogin(authorEmail, "Author");
        registerVerifyLogin(viewerEmail, "Viewer");
        inviteAndAccept(owner.token, owner.workspaceId, authorEmail, "author", List.of(owner.projectId));
        inviteAndAccept(owner.token, owner.workspaceId, viewerEmail, "viewer", List.of(owner.projectId));
        String authorToken = login(authorEmail);
        String viewerToken = login(viewerEmail);

        SuggestSettingsEntity settings = seedSettings(owner);
        SuggestItemEntity item = seedPendingItem(owner, settings);
        String approvePath = "/api/v1/suggest/items/" + item.getId() + "/approve";

        mockMvc.perform(post(approvePath)
                        .with(csrf())
                        .header("Authorization", bearer(authorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        mockMvc.perform(post(approvePath)
                        .with(csrf())
                        .header("Authorization", bearer(viewerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        mockMvc.perform(post(approvePath)
                        .with(csrf())
                        .header("Authorization", bearer(owner.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    private Fixture seedOwnerWithChannel(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        String token = registerVerifyLogin(email, "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        UUID userId = UUID.fromString(readJson(mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn())
                .path("id")
                .asText());
        UUID projectId = UUID.fromString(createProject(token, workspaceId, "Suggest " + prefix, "#112233")
                .get("id")
                .asText());
        UUID channelId = seedChannel(workspaceId, projectId, "VK " + prefix);
        return new Fixture(token, userId, workspaceId, projectId, channelId);
    }

    private SuggestSettingsEntity seedSettings(Fixture fixture) {
        return settingsRepository.findByChannelId(fixture.channelId).orElseGet(() -> {
            SuggestSettingsEntity entity = new SuggestSettingsEntity();
            entity.setId(UuidV7.generate());
            entity.setWorkspaceId(fixture.workspaceId);
            entity.setProjectId(fixture.projectId);
            entity.setChannelId(fixture.channelId);
            entity.setStatus("draft");
            entity.setMode("manual");
            entity.setConfig(Map.of());
            entity.setConfigVersion(1);
            entity.setCreatedBy(fixture.userId);
            Instant now = Instant.now();
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            return settingsRepository.save(entity);
        });
    }

    private SuggestItemEntity seedPendingItem(Fixture fixture, SuggestSettingsEntity settings) {
        SuggestItemEntity item = new SuggestItemEntity();
        item.setId(UuidV7.generate());
        item.setWorkspaceId(fixture.workspaceId);
        item.setChannelId(fixture.channelId);
        item.setSettingsId(settings.getId());
        item.setVkPostId("vk-" + UUID.randomUUID());
        item.setAuthorVkId("1001");
        item.setText("pending suggest");
        item.setStatus("pending_moderation");
        Instant now = Instant.now();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return itemRepository.save(item);
    }

    private UUID seedChannel(UUID workspaceId, UUID projectId, String title) {
        ChannelEntity channel = new ChannelEntity();
        channel.setId(UuidV7.generate());
        channel.setWorkspaceId(workspaceId);
        channel.setProjectId(projectId);
        channel.setNetwork("vk");
        channel.setExternalId("suggest-" + UUID.randomUUID());
        channel.setType("group");
        channel.setTitle(title);
        channel.setCapabilities(Map.of("publishSuggested", true));
        channel.setStatus("active");
        channel.setTimezone("Europe/Moscow");
        Instant now = Instant.now();
        channel.setCreatedAt(now);
        channel.setUpdatedAt(now);
        return channelRepository.save(channel).getId();
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

    private JsonNode createProject(String token, UUID workspaceId, String name, String color) throws Exception {
        return readJson(mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "color", color))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private UUID firstWorkspaceId(String accessToken) throws Exception {
        JsonNode list = readJson(mockMvc.perform(get("/api/v1/workspaces").header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn());
        return UUID.fromString(list.get("items").get(0).get("id").asText());
    }

    private String registerVerifyLogin(String email, String name) throws Exception {
        register(email, name);
        String verifyToken = latestMailToken(email);
        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", verifyToken))))
                .andExpect(status().isOk());
        return login(email);
    }

    private void register(String email, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery", "name", name))))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        JsonNode body = readJson(mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery"))))
                .andExpect(status().isOk())
                .andReturn());
        return body.get("accessToken").asText();
    }

    private String latestMailToken(String email) {
        String body = mailSender.messages().stream()
                .filter(m -> m.to().equalsIgnoreCase(email))
                .reduce((a, b) -> b)
                .orElseThrow()
                .body();
        Matcher matcher = TOKEN_IN_BODY.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record Fixture(String token, UUID userId, UUID workspaceId, UUID projectId, UUID channelId) {}
}
