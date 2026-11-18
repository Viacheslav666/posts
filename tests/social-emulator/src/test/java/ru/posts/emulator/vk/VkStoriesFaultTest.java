package ru.posts.emulator.vk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.posts.emulator.control.ScenarioConfig;
import ru.posts.emulator.control.ScenarioService;
import ru.posts.emulator.store.Network;
import ru.posts.emulator.store.PostStore;

@SpringBootTest
@AutoConfigureMockMvc
class VkStoriesFaultTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostStore postStore;

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private StoryTokenStore storyTokenStore;

    @Autowired
    private VkUploadSessionStore uploadSessionStore;

    @BeforeEach
    void reset() {
        postStore.reset();
        scenarioService.reset();
        storyTokenStore.reset();
        uploadSessionStore.reset();
    }

    @Test
    @DisplayName("TC-STORY: VK stories 5xx / 429 / hang do not create stories")
    void storyFaultsDoNotCreate() throws Exception {
        for (String fault : new String[] {"5xx", "429", "hang"}) {
            postStore.reset();
            ScenarioConfig config = ScenarioConfig.defaults();
            config.forNetwork(Network.VK).setForcedError(fault);
            if ("hang".equals(fault)) {
                config.forNetwork(Network.VK).setHangMs(50);
            }
            if ("429".equals(fault)) {
                config.forNetwork(Network.VK).setRetryAfterSeconds(2);
            }
            if ("5xx".equals(fault)) {
                config.forNetwork(Network.VK).setHttpStatus(503);
            }
            scenarioService.update(config);

            var result = mockMvc.perform(post("/method/stories.save")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("guid", "story-fault-" + fault)
                    .param("group_id", "100")
                    .param("access_token", "t"));
            if ("hang".equals(fault)) {
                result.andExpect(status().is(499));
            } else if ("429".equals(fault)) {
                result.andExpect(status().isTooManyRequests());
            } else {
                result.andExpect(status().is5xxServerError());
            }
            assertThat(postStore.counts().getOrDefault("vk", 0L)).isZero();
        }
    }

    @Test
    @DisplayName("TC-STORY-20: revoked story token → VK error 5 on stories.save")
    void revokedStoryTokenReturnsAuthError() throws Exception {
        mockMvc.perform(post("/_control/vk/story-tokens/100/revoke")).andExpect(status().isOk());

        mockMvc.perform(post("/method/stories.getPhotoUploadServer")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("group_id", "100")
                        .param("access_token", "t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.error_code").value(5));

        mockMvc.perform(post("/method/stories.save")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("guid", "revoked-1")
                        .param("group_id", "100")
                        .param("access_token", "t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.error_code").value(5));

        assertThat(postStore.counts().getOrDefault("vk", 0L)).isZero();
    }

    @Test
    @DisplayName("TC-STORY-01/04: video upload server + save stamps type=video")
    void videoStoryUploadAndSave() throws Exception {
        MvcResult server = mockMvc.perform(post("/method/stories.getVideoUploadServer")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("group_id", "200")
                        .param("guid", "video-1")
                        .param("access_token", "t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.upload_url").exists())
                .andReturn();

        String body = server.getResponse().getContentAsString();
        int start = body.indexOf("/vk/upload/");
        int end = body.indexOf('"', start);
        String uploadPath = body.substring(start, end);

        mockMvc.perform(multipart(uploadPath).file("video_file", "fake-video".getBytes()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.video_id").exists());

        mockMvc.perform(post("/method/stories.save")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("guid", "video-1")
                        .param("group_id", "200")
                        .param("access_token", "t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.items[0].type").value("video"));

        assertThat(postStore.counts().get("vk")).isEqualTo(1L);
    }
}
