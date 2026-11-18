package ru.posts.api.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * QA-11 TC-STORY-27: contract check for {@code features.stories} in network capabilities.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class NetworkCapabilitiesStoriesIT {

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

    @Test
    @DisplayName("TC-STORY-27: GET /api/v1/networks/capabilities — vk/ok stories=true, telegram=false, max false/absent")
    void storiesCapabilityFlags() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/networks/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.networks").isArray())
                .andReturn();

        JsonNode networks = objectMapper.readTree(result.getResponse().getContentAsString()).path("networks");
        assertThat(networks.isArray()).isTrue();

        JsonNode vk = findNetwork(networks, "vk");
        JsonNode ok = findNetwork(networks, "ok");
        JsonNode tg = findNetwork(networks, "tg");
        JsonNode max = findNetwork(networks, "max");

        assertThat(vk.path("features").path("stories").asBoolean()).isTrue();
        assertThat(ok.path("features").path("stories").asBoolean()).isTrue();
        assertThat(tg.path("features").path("stories").asBoolean()).isFalse();

        JsonNode maxStories = max.path("features").path("stories");
        assertThat(maxStories.isMissingNode() || maxStories.isNull() || !maxStories.asBoolean())
                .as("max features.stories should be false or absent")
                .isTrue();
    }

    private static JsonNode findNetwork(JsonNode networks, String id) {
        return StreamSupport.stream(networks.spliterator(), false)
                .filter(n -> id.equals(n.path("id").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Network not found: " + id));
    }
}
