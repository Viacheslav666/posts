package ru.posts.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
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

/**
 * Browser-shaped auth flow without {@code .with(csrf())}: GET to obtain {@code XSRF-TOKEN}, then POST with header.
 * Also covers stale Bearer on public auth (must not 401).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AuthBrowserCsrfIntegrationTest {

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

    @Test
    @DisplayName("GET ping sets XSRF cookie; register/login without MockMvc csrf() helper")
    void registerWithCookieCsrfLikeBrowser() throws Exception {
        String email = "browser-csrf-" + UUID.randomUUID() + "@example.com";
        Cookie[] cookies = pingAndXsrfCookie();

        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(cookies)
                        .header("X-XSRF-TOKEN", xsrfHeader(cookies))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "correct-horse-battery",
                                "name", "Browser CSRF"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("verification_email_sent"));
    }

    @Test
    @DisplayName("stale Bearer on register is ignored (lenient resolver), CSRF still required")
    void registerWithStaleBearerAndCsrf() throws Exception {
        String email = "stale-bearer-" + UUID.randomUUID() + "@example.com";
        Cookie[] cookies = pingAndXsrfCookie();

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", "Bearer not.a.valid.jwt")
                        .cookie(cookies)
                        .header("X-XSRF-TOKEN", xsrfHeader(cookies))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "correct-horse-battery",
                                "name", "Stale Bearer"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("missing XSRF on mutating auth returns 403, not masked 401")
    void registerWithoutCsrfReturnsForbidden() throws Exception {
        String email = "no-csrf-" + UUID.randomUUID() + "@example.com";
        Cookie[] cookies = pingAndXsrfCookie();

        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(cookies)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "correct-horse-battery",
                                "name", "No CSRF"))))
                .andExpect(status().isForbidden());
    }

    private Cookie[] pingAndXsrfCookie() throws Exception {
        MvcResult ping =
                mockMvc.perform(get("/api/v1/ping")).andExpect(status().isOk()).andReturn();
        Cookie[] cookies = ping.getResponse().getCookies();
        assertThat(Arrays.stream(cookies).anyMatch(c -> "XSRF-TOKEN".equals(c.getName())))
                .as("ping must issue XSRF-TOKEN cookie")
                .isTrue();
        return cookies;
    }

    private static String xsrfHeader(Cookie[] cookies) {
        return Arrays.stream(cookies)
                .filter(c -> "XSRF-TOKEN".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow();
    }
}
