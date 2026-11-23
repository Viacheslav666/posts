package ru.posts.emulator.vk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.posts.emulator.control.ScenarioConfig;
import ru.posts.emulator.control.ScenarioService;
import ru.posts.emulator.store.ModerationStore;
import ru.posts.emulator.store.Network;
import ru.posts.emulator.store.PostStore;

@SpringBootTest
@AutoConfigureMockMvc
class VkApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostStore postStore;

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private ModerationStore moderationStore;

    @BeforeEach
    void reset() {
        postStore.reset();
        scenarioService.reset();
        moderationStore.reset();
    }

    @Test
    void wallPostCreatesPostAndIsIdempotentByGuid() throws Exception {
        mockMvc.perform(post("/method/wall.post")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("owner_id", "-100")
                        .param("message", "hello")
                        .param("guid", "pub-1")
                        .param("access_token", "token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.post_id").exists());

        mockMvc.perform(post("/method/wall.post")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("owner_id", "-100")
                        .param("message", "hello again")
                        .param("guid", "pub-1")
                        .param("access_token", "token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.post_already_exists").value(1));

        assertThat(postStore.counts().get("vk")).isEqualTo(1L);
        assertThat(postStore.duplicateAttemptCounts().get("vk")).isEqualTo(1L);
    }

    @Test
    void forcedVkError6() throws Exception {
        ScenarioConfig config = ScenarioConfig.defaults();
        config.forNetwork(Network.VK).setForcedError("6");
        scenarioService.update(config);

        mockMvc.perform(post("/method/wall.post")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("owner_id", "-1")
                        .param("message", "x")
                        .param("guid", "g"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.error_code").value(6));

        assertThat(postStore.counts().get("vk")).isZero();
    }

    @Test
    void forcedErrorsNineTenFifteenHundred() throws Exception {
        for (int code : new int[] {9, 10, 15, 100}) {
            postStore.reset();
            ScenarioConfig config = ScenarioConfig.defaults();
            config.forNetwork(Network.VK).setForcedError(String.valueOf(code));
            scenarioService.update(config);

            mockMvc.perform(post("/vk/method/wall.post")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("owner_id", "-1")
                            .param("message", "x")
                            .param("guid", "g-" + code))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error.error_code").value(code));
        }
    }

    @Test
    void storiesPhotoUploadAndSaveAreIdempotent() throws Exception {
        mockMvc.perform(post("/method/stories.getPhotoUploadServer")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("group_id", "100")
                        .param("link_text", "go_to")
                        .param("link_url", "https://vk.com/club100")
                        .param("guid", "story-1")
                        .param("access_token", "t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.upload_url").exists());

        mockMvc.perform(post("/method/stories.save")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("guid", "story-1")
                        .param("group_id", "100")
                        .param("access_token", "t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.items[0].id").exists());

        mockMvc.perform(post("/method/stories.save")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("guid", "story-1")
                        .param("group_id", "100")
                        .param("access_token", "t"))
                .andExpect(status().isOk());

        assertThat(postStore.counts().get("vk")).isEqualTo(1L);
        assertThat(postStore.duplicateAttemptCounts().get("vk")).isEqualTo(1L);
    }

    @Test
    void photoUploadFlow() throws Exception {
        MvcResult server = mockMvc.perform(post("/method/photos.getWallUploadServer")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("group_id", "10")
                        .param("access_token", "t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.upload_url").exists())
                .andReturn();

        String body = server.getResponse().getContentAsString();
        int start = body.indexOf("/vk/upload/");
        int end = body.indexOf('"', start);
        String uploadPath = body.substring(start, end);

        mockMvc.perform(multipart(uploadPath).file("photo", "fake-image-bytes".getBytes()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hash").exists());

        mockMvc.perform(post("/method/photos.saveWallPhoto")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("group_id", "10")
                        .param("photo", "bytes")
                        .param("hash", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response[0].id").exists());
    }

    @Test
    void commentDeleteAndCloseComments() throws Exception {
        MvcResult created = mockMvc.perform(post("/method/wall.post")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("owner_id", "-100")
                        .param("message", "hello")
                        .param("guid", "pub-comment")
                        .param("action_button", "{\"type\":\"open_url\"}")
                        .param("access_token", "token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.post_id").exists())
                .andReturn();
        String postId = jsonNumber(created.getResponse().getContentAsString(), "post_id");
        assertThat(postStore.findByRemoteId(Network.VK, "-100", postId))
                .get()
                .extracting(post -> post.metadata().get("action_button"))
                .isEqualTo("{\"type\":\"open_url\"}");

        MvcResult comment = mockMvc.perform(post("/method/wall.createComment")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("owner_id", "-100")
                        .param("post_id", postId)
                        .param("from_group", "100")
                        .param("message", "первый")
                        .param("attachments", "photo-100_7,video-100_8")
                        .param("access_token", "token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.comment_id").exists())
                .andReturn();
        String commentId = jsonNumber(comment.getResponse().getContentAsString(), "comment_id");
        assertThat(moderationStore.commentMeta(commentId))
                .containsEntry("post_id", postId)
                .containsEntry("from_group", "100")
                .containsEntry("attachments", "photo-100_7,video-100_8");

        mockMvc.perform(post("/method/wall.closeComments")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("owner_id", "-100")
                        .param("post_id", postId)
                        .param("access_token", "token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value(1));
        assertThat(postStore.findByRemoteId(Network.VK, "-100", postId))
                .get()
                .extracting(post -> post.metadata().get("comments_closed"))
                .isEqualTo(true);

        mockMvc.perform(post("/method/wall.delete")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("owner_id", "-100")
                        .param("post_id", postId)
                        .param("access_token", "token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value(1));
        assertThat(postStore.findByRemoteId(Network.VK, "-100", postId)).isEmpty();
    }

    private static String jsonNumber(String body, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
