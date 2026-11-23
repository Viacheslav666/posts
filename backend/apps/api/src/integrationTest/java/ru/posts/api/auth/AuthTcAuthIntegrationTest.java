package ru.posts.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import ru.posts.api.auth.oauth.OAuthUserProfile;
import ru.posts.api.auth.oauth.StubOAuthProviderClient;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AuthTcAuthIntegrationTest {

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

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
    private StubOAuthProviderClient stubOAuthProviderClient;

    @BeforeEach
    void setUp() {
        mailSender.clear();
        stubOAuthProviderClient.clear();
    }

    @Test
    @DisplayName("TC-AUTH-01 register → verify → login")
    void tcAuth01_registerVerifyLogin() throws Exception {
        String email = "tc01-" + UUID.randomUUID() + "@example.com";
        register(email, "correct-horse-battery", "Alice");
        String verifyToken = latestMailToken(email);
        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", verifyToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("email_verified"));

        JsonNode tokens = login(email, "correct-horse-battery", null);
        assertThat(tokens.get("accessToken").asText()).isNotBlank();
        assertThat(tokens.get("refreshToken").asText()).isNotBlank();

        mockMvc.perform(get("/api/v1/me")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    @DisplayName("TC-AUTH-02 duplicate registration is non-enumerating")
    void tcAuth02_duplicateRegistration() throws Exception {
        String email = "tc02-" + UUID.randomUUID() + "@example.com";
        register(email, "correct-horse-battery", "Bob");
        mailSender.clear();
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "correct-horse-battery",
                                "name", "Bob"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("verification_email_sent"));
        assertThat(mailSender.messages()).isEmpty();
    }

    @Test
    @DisplayName("TC-AUTH-03 sixth login attempt within a minute is rate limited")
    void tcAuth03_loginRateLimit() throws Exception {
        String email = "tc03-" + UUID.randomUUID() + "@example.com";
        registerVerify(email, "correct-horse-battery", "Carol");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("email", email, "password", "wrong-password-xx"))))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "wrong-password-xx"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limited"));
    }

    @Test
    @DisplayName("TC-AUTH-04 2FA required; backup code is single-use")
    void tcAuth04_twoFactorAndBackupCodes() throws Exception {
        String email = "tc04-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";
        registerVerify(email, password, "Dana");
        JsonNode tokens = login(email, password, null);
        String access = tokens.get("accessToken").asText();

        MvcResult setup = mockMvc.perform(
                        post("/api/v1/auth/2fa/setup").with(csrf()).header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode setupBody = objectMapper.readTree(setup.getResponse().getContentAsString());
        String secret = setupBody.get("secret").asText();
        String backup = setupBody.get("backupCodes").get(0).asText();
        String totp = currentTotp(secret);

        mockMvc.perform(post("/api/v1/auth/2fa/confirm")
                        .with(csrf())
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", totp))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("totp_required"));

        JsonNode withBackup = login(email, password, backup);
        assertThat(withBackup.get("accessToken").asText()).isNotBlank();

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", password, "totpCode", backup))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-AUTH-05 refresh reuse revokes the session family")
    void tcAuth05_refreshReuseDetection() throws Exception {
        String email = "tc05-" + UUID.randomUUID() + "@example.com";
        registerVerify(email, "correct-horse-battery", "Eve");
        JsonNode first = login(email, "correct-horse-battery", null);
        String oldRefresh = first.get("refreshToken").asText();

        JsonNode rotated = refresh(oldRefresh);
        String newRefresh = rotated.get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", oldRefresh))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", newRefresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-AUTH-06 password change ends other sessions")
    void tcAuth06_passwordChangeRevokesOthers() throws Exception {
        String email = "tc06-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";
        registerVerify(email, password, "Frank");
        JsonNode sessionA = login(email, password, null);
        JsonNode sessionB = login(email, password, null);

        mockMvc.perform(post("/api/v1/me/password")
                        .with(csrf())
                        .header(
                                "Authorization",
                                "Bearer " + sessionA.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPassword", password, "newPassword", "new-correct-horse"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", sessionB.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", sessionA.get("refreshToken").asText()))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-AUTH-07 OAuth link to existing verified email requires confirmation")
    void tcAuth07_oauthLinkRequiresConfirmation() throws Exception {
        String email = "tc07-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";
        registerVerify(email, password, "Grace");

        stubOAuthProviderClient.registerCode(
                "vk-code-1",
                new OAuthUserProfile("vk", "vk-user-77", email, true, "Grace VK", Map.of("id", "vk-user-77")));

        MvcResult start = mockMvc.perform(post("/api/v1/auth/oauth/vk/start").with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        String state = objectMapper
                .readTree(start.getResponse().getContentAsString())
                .get("state")
                .asText();

        MvcResult callback = mockMvc.perform(post("/api/v1/auth/oauth/vk/callback")
                        .with(csrf())
                        .param("code", "vk-code-1")
                        .param("state", state))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("oauth_link_required"))
                .andReturn();
        String linkToken = objectMapper
                .readTree(callback.getResponse().getContentAsString())
                .get("linkToken")
                .asText();

        JsonNode tokens = login(email, password, null);
        mockMvc.perform(post("/api/v1/auth/oauth/link")
                        .with(csrf())
                        .header(
                                "Authorization",
                                "Bearer " + tokens.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("linkToken", linkToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("oauth_linked"));

        stubOAuthProviderClient.registerCode(
                "vk-code-2",
                new OAuthUserProfile("vk", "vk-user-77", email, true, "Grace VK", Map.of("id", "vk-user-77")));
        MvcResult start2 = mockMvc.perform(post("/api/v1/auth/oauth/vk/start").with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        String state2 = objectMapper
                .readTree(start2.getResponse().getContentAsString())
                .get("state")
                .asText();
        mockMvc.perform(post("/api/v1/auth/oauth/vk/callback")
                        .with(csrf())
                        .param("code", "vk-code-2")
                        .param("state", state2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("VK ID callback passes device_id and state to the provider exchange")
    void vkIdCallbackPassesDeviceIdAndState() throws Exception {
        String userId = "vk-dev-" + UUID.randomUUID();
        String email = "vkdev-" + UUID.randomUUID() + "@example.com";
        stubOAuthProviderClient.registerCode(
                "vk-code-device", new OAuthUserProfile("vk", userId, email, true, "Dev VK", Map.of("id", userId)));
        String state = oauthStartState("vk");

        mockMvc.perform(post("/api/v1/auth/oauth/vk/callback")
                        .with(csrf())
                        .param("code", "vk-code-device")
                        .param("state", state)
                        .param("device_id", "vk-device-42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        var exchange = stubOAuthProviderClient.exchangeFor("vk-code-device");
        assertThat(exchange).isNotNull();
        assertThat(exchange.deviceId()).isEqualTo("vk-device-42");
        assertThat(exchange.state()).isEqualTo(state);
        assertThat(exchange.codeVerifier()).isNotBlank();
        assertThat(exchange.redirectUri()).endsWith("/auth/callback/vk");
    }

    @Test
    @DisplayName("VK ID callback without email creates an account by VK user id")
    void vkIdCallbackWithoutEmailCreatesAccount() throws Exception {
        String userId = "vk-noemail-" + UUID.randomUUID();
        stubOAuthProviderClient.registerCode(
                "vk-code-noemail", new OAuthUserProfile("vk", userId, null, false, "No Mail", Map.of("id", userId)));
        String state = oauthStartState("vk");

        MvcResult callback = mockMvc.perform(post("/api/v1/auth/oauth/vk/callback")
                        .with(csrf())
                        .param("code", "vk-code-noemail")
                        .param("state", state)
                        .param("device_id", "vk-device-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String accessToken = objectMapper
                .readTree(callback.getResponse().getContentAsString())
                .get("accessToken")
                .asText();

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("vk+" + userId + "@oauth.posts.local"))
                .andExpect(jsonPath("$.name").value("No Mail"));
    }

    private String oauthStartState(String provider) throws Exception {
        MvcResult start = mockMvc.perform(
                        post("/api/v1/auth/oauth/" + provider + "/start").with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper
                .readTree(start.getResponse().getContentAsString())
                .get("state")
                .asText();
    }

    @Test
    @DisplayName("sessions list and revoke")
    void sessionsListAndRevoke() throws Exception {
        String email = "sess-" + UUID.randomUUID() + "@example.com";
        registerVerify(email, "correct-horse-battery", "Hank");
        JsonNode a = login(email, "correct-horse-battery", null);
        login(email, "correct-horse-battery", null);

        mockMvc.perform(get("/api/v1/me/sessions")
                        .header(
                                "Authorization",
                                "Bearer " + a.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        mockMvc.perform(delete("/api/v1/me/sessions")
                        .with(csrf())
                        .header(
                                "Authorization",
                                "Bearer " + a.get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    private void register(String email, String password, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", password, "name", name))))
                .andExpect(status().isCreated());
    }

    private void registerVerify(String email, String password, String name) throws Exception {
        register(email, password, name);
        String token = latestMailToken(email);
        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isOk());
    }

    private JsonNode login(String email, String password, String totpCode) throws Exception {
        var body = objectMapper.createObjectNode();
        body.put("email", email);
        body.put("password", password);
        if (totpCode != null) {
            body.put("totpCode", totpCode);
        }
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String latestMailToken(String email) {
        return mailSender.messages().stream()
                .filter(m -> m.to().equalsIgnoreCase(email))
                .reduce((a, b) -> b)
                .map(m -> {
                    Matcher matcher = TOKEN_IN_LINK.matcher(m.body());
                    assertThat(matcher.find()).isTrue();
                    return matcher.group(1);
                })
                .orElseThrow(() -> new AssertionError("No mail for " + email));
    }

    private String currentTotp(String secret) {
        // Use library verifier path via generating with DefaultCodeGenerator through TotpService.verify loop windows.
        // Produce a code by probing nearby epochs is unnecessary: confirm uses TotpService.verify against
        // authenticator.
        // Generate via reflection-free approach: call setup secret with known algorithm from TotpService indirectly.
        try {
            var generator = new dev.samstevens.totp.code.DefaultCodeGenerator(
                    dev.samstevens.totp.code.HashingAlgorithm.SHA1, 6);
            long counter = Math.floorDiv(System.currentTimeMillis() / 1000, 30);
            return generator.generate(secret, counter);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
