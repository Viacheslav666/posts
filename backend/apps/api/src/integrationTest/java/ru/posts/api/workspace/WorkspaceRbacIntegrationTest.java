package ru.posts.api.workspace;

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
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.WorkspaceRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class WorkspaceRbacIntegrationTest {

    private static final Pattern TOKEN_IN_BODY = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoggingAuthMailSender mailSender;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @BeforeEach
    void setUp() {
        mailSender.clear();
    }

    @Test
    @DisplayName("TC-WS-01 personal workspace after registration; TC-WS-02 second workspace isolated")
    void tcWs01_02_workspaces() throws Exception {
        String email = "ws01-" + UUID.randomUUID() + "@example.com";
        String token = registerVerifyLogin(email, "Owner One");
        JsonNode list = readJson(mockMvc.perform(get("/api/v1/workspaces").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(list.get("items")).hasSize(1);
        UUID personalId = UUID.fromString(list.get("items").get(0).get("id").asText());
        assertThat(list.get("items").get(0).get("name").asText()).isEqualTo("Личное пространство");

        JsonNode created = readJson(mockMvc.perform(post("/api/v1/workspaces")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Клиентский WS"))))
                .andExpect(status().isCreated())
                .andReturn());
        UUID secondId = UUID.fromString(created.get("id").asText());
        assertThat(secondId).isNotEqualTo(personalId);

        mockMvc.perform(get("/api/v1/workspaces/" + secondId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Клиентский WS"));
    }

    @Test
    @DisplayName("TC-IDOR-01 foreign workspace / project by UUID → 404 (not 403)")
    void tcIdor01_foreignWorkspace() throws Exception {
        String ownerA = registerVerifyLogin("idor-a-" + UUID.randomUUID() + "@example.com", "Owner A");
        String ownerB = registerVerifyLogin("idor-b-" + UUID.randomUUID() + "@example.com", "Owner B");

        UUID wsA = firstWorkspaceId(ownerA);
        UUID wsB = firstWorkspaceId(ownerB);
        UUID projectB = UUID.fromString(
                createProject(ownerB, wsB, "Foreign", "#ABCDEF").get("id").asText());

        mockMvc.perform(get("/api/v1/workspaces/" + wsB).header("Authorization", bearer(ownerA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
        mockMvc.perform(put("/api/v1/workspaces/" + wsB)
                        .with(csrf())
                        .header("Authorization", bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Hacked"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/workspaces/" + wsB).with(csrf()).header("Authorization", bearer(ownerA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/workspaces/" + wsA + "/audit").header("Authorization", bearer(ownerB)))
                .andExpect(status().isNotFound());
        // Same IDOR contract for project UUID from another workspace
        mockMvc.perform(get("/api/v1/projects/" + projectB).header("Authorization", bearer(ownerA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
        mockMvc.perform(put("/api/v1/projects/" + projectB)
                        .with(csrf())
                        .header("Authorization", bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Hacked"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    @DisplayName("TC-WS-03 / TC-RBAC-01 / TC-IDOR: editor sees only granted projects; unassigned → 404")
    void tcRbac01_editorAssignedProjects() throws Exception {
        Fixture fx = seedTeamWorkspace();

        mockMvc.perform(get("/api/v1/projects/" + fx.projectAlpha).header("Authorization", bearer(fx.editorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha"));

        // Outside ProjectGrant must be not_found (404), never forbidden (403)
        mockMvc.perform(get("/api/v1/projects/" + fx.projectBeta).header("Authorization", bearer(fx.editorToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.status").value(404));

        JsonNode editorProjects = readJson(mockMvc.perform(get("/api/v1/workspaces/" + fx.workspaceId + "/projects")
                        .header("Authorization", bearer(fx.editorToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(editorProjects.get("items")).hasSize(1);
        assertThat(editorProjects.get("items").get(0).get("id").asText()).isEqualTo(fx.projectAlpha.toString());
    }

    @Test
    @DisplayName("TC-RBAC-03 viewer invite denied; TC-RBAC-04 audit.view denied for editor")
    void tcRbac03_04_viewerAndAudit() throws Exception {
        Fixture fx = seedTeamWorkspace();

        mockMvc.perform(post("/api/v1/workspaces/" + fx.workspaceId + "/invitations")
                        .with(csrf())
                        .header("Authorization", bearer(fx.viewerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "someone@" + UUID.randomUUID() + ".example.com", "role", "viewer"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        mockMvc.perform(get("/api/v1/workspaces/" + fx.workspaceId + "/audit")
                        .header("Authorization", bearer(fx.editorToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        mockMvc.perform(get("/api/v1/workspaces/" + fx.workspaceId + "/audit")
                        .header("Authorization", bearer(fx.ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    @DisplayName("TC-RBAC-05 admin cannot delete workspace; owner can")
    void tcRbac05_ownerOnlyDelete() throws Exception {
        Fixture fx = seedTeamWorkspace();

        mockMvc.perform(delete("/api/v1/workspaces/" + fx.workspaceId)
                        .with(csrf())
                        .header("Authorization", bearer(fx.adminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        // Owner deletes a disposable second workspace
        JsonNode extra = readJson(mockMvc.perform(post("/api/v1/workspaces")
                        .with(csrf())
                        .header("Authorization", bearer(fx.ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Disposable"))))
                .andExpect(status().isCreated())
                .andReturn());
        UUID disposable = UUID.fromString(extra.get("id").asText());
        mockMvc.perform(delete("/api/v1/workspaces/" + disposable)
                        .with(csrf())
                        .header("Authorization", bearer(fx.ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("workspace_deleted"));
        mockMvc.perform(get("/api/v1/workspaces/" + disposable).header("Authorization", bearer(fx.ownerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-WS-04 invite + accept TTL 7d; TC-WS-06 revoked invite blocked; TC-WS-07 audit entries")
    void tcWs04_06_07_invitationsAndAudit() throws Exception {
        String ownerEmail = "inv-owner-" + UUID.randomUUID() + "@example.com";
        String inviteeEmail = "inv-editor-" + UUID.randomUUID() + "@example.com";
        String ownerToken = registerVerifyLogin(ownerEmail, "Inv Owner");
        registerVerifyLogin(inviteeEmail, "Inv Editor");

        UUID workspaceId = firstWorkspaceId(ownerToken);
        upgradeToAgency(workspaceId);

        JsonNode alpha = createProject(ownerToken, workspaceId, "Alpha", "#111111");

        JsonNode invitation = readJson(mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .with(csrf())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email",
                                inviteeEmail,
                                "role",
                                "editor",
                                "projectIds",
                                List.of(alpha.get("id").asText())))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("editor"))
                .andReturn());

        Instantish expires = Instantish.parse(invitation.get("expiresAt").asText());
        assertThat(expires.daysFromNow()).isBetween(6.0, 7.5);

        String inviteToken = latestMailToken(inviteeEmail);
        mockMvc.perform(get("/api/v1/invitations/preview").param("token", inviteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(inviteeEmail));

        String inviteeToken = login(inviteeEmail);
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .with(csrf())
                        .header("Authorization", bearer(inviteeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", inviteToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workspaceId.toString()));

        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", bearer(inviteeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(alpha.get("id").asText()));

        // Revoked invite
        String otherEmail = "revoked-" + UUID.randomUUID() + "@example.com";
        registerVerifyLogin(otherEmail, "Other");
        JsonNode pending = readJson(mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .with(csrf())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", otherEmail, "role", "viewer"))))
                .andExpect(status().isCreated())
                .andReturn());
        String revokedToken = latestMailToken(otherEmail);
        mockMvc.perform(delete("/api/v1/invitations/" + pending.get("id").asText())
                        .with(csrf())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .with(csrf())
                        .header("Authorization", bearer(login(otherEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", revokedToken))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_token"));

        JsonNode audit = readJson(mockMvc.perform(
                        get("/api/v1/workspaces/" + workspaceId + "/audit").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(audit.get("items").size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("TC-WS-05 seat limit on free plan → 402")
    void tcWs05_seatLimit() throws Exception {
        String ownerToken = registerVerifyLogin("seats-" + UUID.randomUUID() + "@example.com", "Seats Owner");
        UUID workspaceId = firstWorkspaceId(ownerToken);
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .with(csrf())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "overflow-" + UUID.randomUUID() + "@example.com", "role", "viewer"))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("plan_limit_exceeded"));
    }

    private Fixture seedTeamWorkspace() throws Exception {
        String ownerEmail = "owner-" + UUID.randomUUID() + "@example.com";
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        String editorEmail = "editor-" + UUID.randomUUID() + "@example.com";
        String viewerEmail = "viewer-" + UUID.randomUUID() + "@example.com";

        String ownerToken = registerVerifyLogin(ownerEmail, "Owner");
        registerVerifyLogin(adminEmail, "Admin");
        registerVerifyLogin(editorEmail, "Editor");
        registerVerifyLogin(viewerEmail, "Viewer");

        UUID workspaceId = firstWorkspaceId(ownerToken);
        upgradeToAgency(workspaceId);

        UUID projectAlpha = UUID.fromString(createProject(ownerToken, workspaceId, "Alpha", "#AA0000")
                .get("id")
                .asText());
        UUID projectBeta = UUID.fromString(createProject(ownerToken, workspaceId, "Beta", "#00AA00")
                .get("id")
                .asText());

        inviteAndAccept(ownerToken, workspaceId, adminEmail, "admin", List.of(projectAlpha, projectBeta));
        inviteAndAccept(ownerToken, workspaceId, editorEmail, "editor", List.of(projectAlpha));
        inviteAndAccept(ownerToken, workspaceId, viewerEmail, "viewer", List.of(projectAlpha));

        return new Fixture(
                workspaceId,
                projectAlpha,
                projectBeta,
                ownerToken,
                login(adminEmail),
                login(editorEmail),
                login(viewerEmail));
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

    private void upgradeToAgency(UUID workspaceId) {
        PlanEntity agency = planRepository.findByCode("agency").orElseThrow();
        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        workspace.setPlanId(agency.getId());
        workspaceRepository.save(workspace);
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
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "correct-horse-battery",
                                "name", name))))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        JsonNode tokens = readJson(mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery"))))
                .andExpect(status().isOk())
                .andReturn());
        return tokens.get("accessToken").asText();
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

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private record Fixture(
            UUID workspaceId,
            UUID projectAlpha,
            UUID projectBeta,
            String ownerToken,
            String adminToken,
            String editorToken,
            String viewerToken) {}

    /** Tiny helper to assert invitation TTL without pulling java.time into assertions heavily. */
    private record Instantish(java.time.Instant value) {
        static Instantish parse(String iso) {
            return new Instantish(java.time.Instant.parse(iso));
        }

        double daysFromNow() {
            return java.time.Duration.between(java.time.Instant.now(), value).toMinutes() / 1440.0;
        }
    }
}
