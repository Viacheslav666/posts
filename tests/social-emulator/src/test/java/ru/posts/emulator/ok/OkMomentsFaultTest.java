package ru.posts.emulator.ok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.posts.emulator.control.ScenarioConfig;
import ru.posts.emulator.control.ScenarioService;
import ru.posts.emulator.store.Network;
import ru.posts.emulator.store.PostStore;

@SpringBootTest
@AutoConfigureMockMvc
class OkMomentsFaultTest {

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
    @DisplayName("TC-STORY-02 faults: OK moments 5xx / 429 / hang do not create moments")
    void momentFaultsDoNotCreate() throws Exception {
        for (String fault : new String[] {"5xx", "429", "hang"}) {
            postStore.reset();
            ScenarioConfig config = ScenarioConfig.defaults();
            config.forNetwork(Network.OK).setForcedError(fault);
            if ("hang".equals(fault)) {
                config.forNetwork(Network.OK).setHangMs(50);
            }
            if ("429".equals(fault)) {
                config.forNetwork(Network.OK).setRetryAfterSeconds(3);
            }
            if ("5xx".equals(fault)) {
                config.forNetwork(Network.OK).setHttpStatus(502);
            }
            scenarioService.update(config);

            var result = mockMvc.perform(post("/ok/fb.do")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("method", "moments.commit")
                    .param("owner_ref", "group:77")
                    .param("ttl_ms", "86400000")
                    .param("idempotency_key", "moment-fault-" + fault)
                    .param("access_token", "t"));
            if ("hang".equals(fault)) {
                result.andExpect(status().is(499));
            } else if ("429".equals(fault)) {
                result.andExpect(status().isTooManyRequests());
            } else {
                result.andExpect(status().is5xxServerError());
            }
            assertThat(postStore.counts().getOrDefault("ok", 0L)).isZero();
        }
    }
}
