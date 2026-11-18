package ru.posts.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import ru.posts.api.error.ProblemCodes;
import ru.posts.messaging.topology.PublishTopology;
import ru.posts.persistence.entity.AdminAuditLogEntity;
import ru.posts.persistence.entity.UserEntity;
import ru.posts.persistence.repository.AdminAuditLogRepository;
import ru.posts.persistence.repository.UserRepository;
import ru.posts.security.auth.SecurityAuthSupport;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AdminPlatformApiIT {

    private static final Pattern TOKEN_IN_BODY = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final String PASSWORD = "Password1!Password1!";

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
    UserRepository userRepository;

    @Autowired
    AdminAuditLogRepository adminAuditLogRepository;

    @Autowired
    InMemoryAdminQueueService inMemoryAdminQueueService;

    private String adminToken;
    private UUID adminUserId;
    private String userToken;
    private UUID userId;
    private UUID workspaceId;
    private String adminEmail;
    private String userEmail;

    @BeforeEach
    void setUp() throws Exception {
        mailSender.clear();
        adminEmail = "platform-admin-" + UUID.randomUUID() + "@example.com";
        userEmail = "regular-user-" + UUID.randomUUID() + "@example.com";
        adminToken = registerVerifyLogin(adminEmail, "Platform Admin");
        adminUserId = currentUserId(adminToken);
        UserEntity admin = userRepository.findById(adminUserId).orElseThrow();
        admin.setPlatformAdmin(true);
        userRepository.save(admin);

        userToken = registerVerifyLogin(userEmail, "Regular User");
        JsonNode me = readJson(mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andReturn());
        userId = UUID.fromString(me.get("id").asText());
        workspaceId =
                UUID.fromString(me.get("memberships").get(0).get("workspaceId").asText());
    }

    @Test
    @DisplayName("non-admin gets 403 platform_admin_required on /admin")
    void nonAdminForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ProblemCodes.PLATFORM_ADMIN_REQUIRED));
    }

    @Test
    @DisplayName("admin can search users and open workspace card")
    void adminSearchAndWorkspaceCard() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("q", "regular-user")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(userId.toString()));

        mockMvc.perform(get("/api/v1/admin/workspaces/" + workspaceId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workspaceId.toString()))
                .andExpect(jsonPath("$.planCode").exists())
                .andExpect(jsonPath("$.channels").isArray())
                .andExpect(jsonPath("$.recentPublicationErrors").isArray());
    }

    @Test
    @DisplayName("impersonation issues token with imp claim and writes immutable audit")
    void impersonationAudited() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/users/" + userId + "/impersonate")
                        .header("Authorization", bearer(adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "support investigation"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impersonating").value(true))
                .andExpect(jsonPath("$.targetUserId").value(userId.toString()))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String impToken = body.get("accessToken").asText();
        SignedJWT jwt = SignedJWT.parse(impToken);
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getJWTClaimsSet().getBooleanClaim(SecurityAuthSupport.CLAIM_IMPERSONATING))
                .isTrue();
        assertThat(jwt.getJWTClaimsSet().getStringClaim(SecurityAuthSupport.CLAIM_ACTOR))
                .isEqualTo(adminUserId.toString());

        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(impToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impersonation.active").value(true))
                .andExpect(jsonPath("$.impersonation.actorUserId").value(adminUserId.toString()));

        mockMvc.perform(post("/api/v1/me/password")
                        .header("Authorization", bearer(impToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPassword", PASSWORD, "newPassword", "Another1!Password1!"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ProblemCodes.IMPERSONATION_FORBIDDEN));

        var audits = adminAuditLogRepository.findFiltered(adminUserId, "admin.impersonate", "user", userId);
        assertThat(audits).isNotEmpty();
        AdminAuditLogEntity entry = audits.getFirst();
        assertThat(entry.getReason()).isEqualTo("support investigation");
        assertThat(entry.getActorUserId()).isEqualTo(adminUserId);
        assertThat(entry.getTargetId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("admin can manage feature flags, promos, announcements and audit list")
    void adminCatalogOps() throws Exception {
        mockMvc.perform(put("/api/v1/admin/feature-flags")
                        .header("Authorization", bearer(adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "key", "stories.enabled",
                                "description", "Scheduled stories",
                                "enabledGlobal", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("stories.enabled"));

        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant until = Instant.now().plus(30, ChronoUnit.DAYS);
        mockMvc.perform(post("/api/v1/admin/promos")
                        .header("Authorization", bearer(adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code",
                                "SAVE10-" + UUID.randomUUID().toString().substring(0, 8),
                                "kind",
                                "percent",
                                "value",
                                new BigDecimal("10"),
                                "validFrom",
                                from.toString(),
                                "validUntil",
                                until.toString(),
                                "stackable",
                                false,
                                "oncePerWorkspace",
                                true,
                                "active",
                                true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("percent"));

        mockMvc.perform(post("/api/v1/admin/announcements")
                        .header("Authorization", bearer(adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Maintenance",
                                "body", "Tonight 02:00 UTC",
                                "severity", "warning",
                                "active", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value("warning"));

        mockMvc.perform(get("/api/v1/admin/audit").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    @DisplayName("DLQ retry is idempotent (no duplicate requeue on same Idempotency-Key)")
    void queueRetryIdempotent() throws Exception {
        String queue = PublishTopology.dlqName("tg");
        String messageId = UUID.randomUUID().toString();
        inMemoryAdminQueueService.seed(queue, messageId, "{\"publicationId\":\"" + messageId + "\"}");
        String key = "retry-" + messageId;

        mockMvc.perform(post("/api/v1/admin/queues/" + queue + "/messages/" + messageId + "/retry")
                        .header("Authorization", bearer(adminToken))
                        .header("Idempotency-Key", key)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true));

        mockMvc.perform(post("/api/v1/admin/queues/" + queue + "/messages/" + messageId + "/retry")
                        .header("Authorization", bearer(adminToken))
                        .header("Idempotency-Key", key)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.detail").value("idempotent_replay"));
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
                .orElseThrow(() -> new AssertionError("No mail for " + email));
        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", verifyToken))))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper
                .readTree(login.getResponse().getContentAsString())
                .path("accessToken")
                .asText();
    }

    private UUID currentUserId(String accessToken) throws Exception {
        JsonNode me = readJson(mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn());
        return UUID.fromString(me.get("id").asText());
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
