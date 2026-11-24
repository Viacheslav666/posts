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
import ru.posts.emulator.store.Network;
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

    @Test
    void grabberMediaReuploadFlow() throws Exception {
        mockMvc.perform(
                        put("/_control/vk/wall/-123")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"items":[{"id":20,"text":"Media","attachments":[
                                  {"type":"video","video":{"id":7,"owner_id":-123,"title":"Public"}},
                                  {"type":"video","video":{"id":8,"owner_id":-123,"is_private":1}}
                                ]}]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/method/video.get").param("videos", "-123_7").param("access_token", "t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.count").value(1))
                .andExpect(jsonPath("$.response.items[0].title").value("Public"))
                .andExpect(jsonPath("$.response.items[0].files.mp4_480").exists());
        mockMvc.perform(post("/method/video.get").param("videos", "-123_8"))
                .andExpect(jsonPath("$.response.items[0].is_private").value(1))
                .andExpect(jsonPath("$.response.items[0].files").doesNotExist());
        mockMvc.perform(post("/method/video.get").param("videos", "-123_404"))
                .andExpect(jsonPath("$.response.count").value(0));

        byte[] mp4 = mockMvc.perform(get("/vk/media/video-123_7.mp4").param("size", "10000"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(mp4).hasSize(10000);
        assertThat(mockMvc.perform(get("/vk/media/p1.jpg"))
                        .andReturn()
                        .getResponse()
                        .getContentType())
                .isEqualTo("image/jpeg");

        mockMvc.perform(post("/method/video.save").param("group_id", "999").param("name", "Public"))
                .andExpect(jsonPath("$.response.upload_url").exists())
                .andExpect(jsonPath("$.response.owner_id").value(-999));

        mockMvc.perform(post("/method/wall.post")
                        .param("owner_id", "-999")
                        .param("message", "grabbed")
                        .param("guid", "grabber-media-1")
                        .param("attachments", "photo-999_1,video-999_2")
                        .param("primary_attachments_mode", "carousel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.post_id").exists());
        assertThat(postStore.list(Network.VK, null).stream()
                        .anyMatch(p ->
                                "photo-999_1,video-999_2".equals(p.metadata().get("attachments"))
                                        && "carousel".equals(p.metadata().get("primary_attachments_mode"))))
                .isTrue();
    }
}
