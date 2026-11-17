package ru.posts.emulator.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class TelegramApiTest {

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
    void sendMessageSucceeds() throws Exception {
        mockMvc.perform(
                        post("/bot123:ABC/sendMessage")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"chat_id":"-1001","text":"hello","idempotency_key":"tg-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.result.message_id").exists());

        assertThat(postStore.counts().get("telegram")).isEqualTo(1L);
    }

    @Test
    void sendPhotoAndMediaGroup() throws Exception {
        mockMvc.perform(
                        post("/telegram/bot123:ABC/sendPhoto")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"chat_id":"-1001","caption":"pic","photo":"https://example.com/a.jpg","idempotency_key":"p1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(
                        post("/bot123:ABC/sendMediaGroup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"chat_id":"-1001","media":"[{}]","idempotency_key":"mg1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.result[0].message_id").exists());
    }

    @Test
    void forced429IncludesRetryAfter() throws Exception {
        mockMvc.perform(
                        put("/_control/scenario")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "networks": {
                                    "telegram": {"forcedError":"429","retryAfterSeconds":11},
                                    "vk":{}, "ok":{}, "max":{}
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/bot123:ABC/sendMessage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"1\",\"text\":\"x\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "11"))
                .andExpect(jsonPath("$.parameters.retry_after").value(11));
    }
}
