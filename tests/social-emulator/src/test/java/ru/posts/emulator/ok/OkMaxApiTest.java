package ru.posts.emulator.ok;

import static org.assertj.core.api.Assertions.assertThat;
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
import ru.posts.emulator.control.ScenarioService;
import ru.posts.emulator.store.PostStore;

@SpringBootTest
@AutoConfigureMockMvc
class OkMaxApiTest {

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
    void okMediatopicPost() throws Exception {
        mockMvc.perform(post("/ok/fb.do")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("method", "mediatopic.post")
                        .param("gid", "42")
                        .param("attachment", "{\"media\":[{\"type\":\"text\",\"text\":\"hi\"}]}")
                        .param("idempotency_key", "ok-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.id").exists());

        assertThat(postStore.counts().get("ok")).isEqualTo(1L);
    }

    @Test
    void maxMessages() throws Exception {
        mockMvc.perform(
                        post("/max/messages")
                                .param("access_token", "tok")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"chat_id":"chan-1","text":"hello max","idempotency_key":"max-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.body.mid").exists());

        assertThat(postStore.counts().get("max")).isEqualTo(1L);
    }

    @Test
    void successWithoutResponseStillCreatesPost() throws Exception {
        mockMvc.perform(
                        put("/_control/scenario")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "networks": {
                                    "vk": {"forcedError":"drop"},
                                    "telegram":{}, "ok":{}, "max":{}
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/method/wall.post")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("owner_id", "-7")
                        .param("message", "lost")
                        .param("guid", "lost-1"))
                .andExpect(status().is(499));

        assertThat(postStore.counts().get("vk")).isEqualTo(1L);
        assertThat(postStore.findByIdempotencyKey(ru.posts.emulator.store.Network.VK, "-7", "lost-1"))
                .isPresent();
    }
}
