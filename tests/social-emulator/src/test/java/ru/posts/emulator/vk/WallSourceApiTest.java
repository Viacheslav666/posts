package ru.posts.emulator.vk;

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
class WallSourceApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PostStore postStore;

    @Autowired
    WallSourceStore wallSourceStore;

    @BeforeEach
    void reset() {
        postStore.reset();
        wallSourceStore.reset();
    }

    @Test
    void seedWallAndFetchViaWallGet() throws Exception {
        mockMvc.perform(
                        put("/_control/vk/wall/-123")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"items":[
                                  {"id":10,"text":"Hello grabber","marked_as_ads":0},
                                  {"id":11,"text":"Ad","marked_as_ads":1}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        mockMvc.perform(get("/method/wall.get").param("owner_id", "-123").param("count", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.count").value(2))
                .andExpect(jsonPath("$.response.items[0].text").value("Hello grabber"));

        mockMvc.perform(post("/_control/vk/wall/-123/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Newest\",\"marked_as_ads\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.text").value("Newest"));

        mockMvc.perform(get("/vk/method/wall.get").param("owner_id", "-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.count").value(3))
                .andExpect(jsonPath("$.response.items[0].text").value("Newest"));

        mockMvc.perform(post("/_control/reset")).andExpect(status().isOk());
        assertThat(wallSourceStore.snapshot()).isEmpty();
    }
}
