package ru.posts.emulator.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.posts.emulator.store.PostStore;

@SpringBootTest
@AutoConfigureMockMvc
class ControlApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostStore postStore;

    @Autowired
    private ScenarioService scenarioService;

    @BeforeEach
    void reset() {
        postStore.reset();
        scenarioService.reset();
    }

    @Test
    void healthIsOk() throws Exception {
        mockMvc.perform(get("/_control/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void scenarioCanBeUpdated() throws Exception {
        mockMvc.perform(
                        put("/_control/scenario")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "globalDelayMs": 0,
                                  "networks": {
                                    "vk": {"forcedError": "6", "delayMs": 10},
                                    "telegram": {"forcedError": "429", "retryAfterSeconds": 7},
                                    "ok": {},
                                    "max": {}
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.networks.vk.forcedError").value("6"))
                .andExpect(jsonPath("$.networks.telegram.retryAfterSeconds").value(7));

        assertThat(scenarioService.network(ru.posts.emulator.store.Network.VK).getForcedError())
                .isEqualTo("6");
    }

    @Test
    void resetClearsPostsAndScenario() throws Exception {
        postStore.createOrGet(ru.posts.emulator.store.Network.VK, "-1", "g1", "hi", java.util.Map.of());
        scenarioService.update(scenarioWithVkError("9"));

        mockMvc.perform(post("/_control/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.total").value(0));

        assertThat(postStore.counts().get("total")).isZero();
        assertThat(scenarioService.network(ru.posts.emulator.store.Network.VK).getForcedError())
                .isNull();
    }

    @Test
    void verifyReportsNoDuplicates() throws Exception {
        postStore.createOrGet(ru.posts.emulator.store.Network.TELEGRAM, "1", "k1", "a", java.util.Map.of());
        postStore.createOrGet(ru.posts.emulator.store.Network.TELEGRAM, "1", "k1", "a", java.util.Map.of());

        mockMvc.perform(get("/_control/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.totalPosts").value(1))
                .andExpect(jsonPath("$.duplicateAttemptsByNetwork.telegram").value(1));
    }

    private static ScenarioConfig scenarioWithVkError(String code) {
        ScenarioConfig config = ScenarioConfig.defaults();
        config.forNetwork(ru.posts.emulator.store.Network.VK).setForcedError(code);
        return config;
    }
}
