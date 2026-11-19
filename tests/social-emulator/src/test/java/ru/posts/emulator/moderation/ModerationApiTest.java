package ru.posts.emulator.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.posts.emulator.control.ScenarioConfig;
import ru.posts.emulator.control.ScenarioService;
import ru.posts.emulator.store.ModerationStore;
import ru.posts.emulator.store.Network;
import ru.posts.emulator.store.PostStore;

@SpringBootTest
@AutoConfigureMockMvc
class ModerationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostStore postStore;

    @Autowired
    private ModerationStore moderationStore;

    @Autowired
    private ScenarioService scenarioService;

    @BeforeEach
    void reset() {
        postStore.reset();
        moderationStore.reset();
        scenarioService.reset();
    }

    @Test
    void vkSuggestSeedPublishAndWallGet() throws Exception {
        mockMvc.perform(
                        put("/_control/vk/suggests")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"ownerId":"-100","items":[{"id":42,"text":"please post"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(post("/method/wall.get")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("owner_id", "-100")
                        .param("filter", "suggests")
                        .param("access_token", "t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.count").value(1))
                .andExpect(jsonPath("$.response.items[0].id").value(42));

        mockMvc.perform(post("/method/wall.post")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("owner_id", "-100")
                        .param("post_id", "42")
                        .param("from_group", "1")
                        .param("guid", "suggest-42")
                        .param("access_token", "t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.post_id").value(42));

        assertThat(moderationStore.listSuggests("-100")).isEmpty();
        assertThat(postStore.findByRemoteId(Network.VK, "-100", "42")).isPresent();

        mockMvc.perform(post("/method/wall.getById")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("posts", "-100_42")
                        .param("access_token", "t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.items[0].id").value(42));
    }

    @Test
    void vkDeleteRestoreCommentAndBan() throws Exception {
        mockMvc.perform(
                        put("/_control/comments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"items":[{"network":"vk","ownerId":"-1","commentId":"9","type":"wall","text":"spam"}]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/method/wall.deleteComment")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("owner_id", "-1")
                        .param("comment_id", "9")
                        .param("access_token", "t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value(1));

        assertThat(moderationStore.findComment(Network.VK, "-1", "9"))
                .isPresent()
                .get()
                .extracting(ModerationStore.EmulatedComment::deleted)
                .isEqualTo(true);

        mockMvc.perform(post("/method/wall.restoreComment")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("owner_id", "-1")
                        .param("comment_id", "9")
                        .param("access_token", "t"))
                .andExpect(status().isOk());

        assertThat(moderationStore.findComment(Network.VK, "-1", "9"))
                .isPresent()
                .get()
                .extracting(c -> c.deleted() || !c.restored())
                .isEqualTo(false);

        mockMvc.perform(post("/method/groups.ban")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("group_id", "1")
                        .param("owner_id", "777")
                        .param("reason", "1")
                        .param("access_token", "t"))
                .andExpect(status().isOk());
        assertThat(moderationStore.isBanned(Network.VK, "1", "777")).isTrue();

        mockMvc.perform(post("/method/groups.unban")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("group_id", "1")
                        .param("owner_id", "777")
                        .param("access_token", "t"))
                .andExpect(status().isOk());
        assertThat(moderationStore.isBanned(Network.VK, "1", "777")).isFalse();
    }

    @Test
    void vkExecuteBatchesCalls() throws Exception {
        mockMvc.perform(
                        put("/_control/comments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"items":[
                                  {"network":"vk","ownerId":"-1","commentId":"1","type":"wall"},
                                  {"network":"vk","ownerId":"-1","commentId":"2","type":"wall"}
                                ]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/method/execute")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param(
                                "code",
                                "return [API.wall.deleteComment({owner_id: -1, comment_id: 1}),"
                                        + " API.wall.deleteComment({owner_id: -1, comment_id: 2})];")
                        .param("access_token", "t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.length()").value(2))
                .andExpect(jsonPath("$.response[0]").value(1));

        assertThat(moderationStore.findComment(Network.VK, "-1", "1"))
                .get()
                .extracting(ModerationStore.EmulatedComment::deleted)
                .isEqualTo(true);
        assertThat(moderationStore.findComment(Network.VK, "-1", "2"))
                .get()
                .extracting(ModerationStore.EmulatedComment::deleted)
                .isEqualTo(true);
    }

    @Test
    void vkPermissionErrorsFifteenTwentySevenTwoOhThree() throws Exception {
        for (int code : new int[] {15, 27, 203}) {
            ScenarioConfig config = ScenarioConfig.defaults();
            config.forNetwork(Network.VK).setForcedError(String.valueOf(code));
            scenarioService.update(config);

            mockMvc.perform(post("/method/wall.deleteComment")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("owner_id", "-1")
                            .param("comment_id", "1")
                            .param("access_token", "t"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error.error_code").value(code));
        }
    }

    @Test
    void telegramDeleteRespects48hWindow() throws Exception {
        postStore.seed(
                Network.TELEGRAM, "-1001", "55", "old", Instant.now().minus(49, ChronoUnit.HOURS), java.util.Map.of());

        mockMvc.perform(post("/bot123:ABC/deleteMessage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"-1001\",\"message_id\":55}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.description").value("Bad Request: message can't be deleted"));

        postStore.seed(Network.TELEGRAM, "-1001", "56", "fresh", Instant.now(), java.util.Map.of());
        mockMvc.perform(post("/bot123:ABC/deleteMessage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"-1001\",\"message_id\":56}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void telegramBanAndGetChat() throws Exception {
        mockMvc.perform(post("/bot123:ABC/banChatMember")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"-1001\",\"user_id\":99,\"until_date\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        assertThat(moderationStore.isBanned(Network.TELEGRAM, "-1001", "99")).isTrue();

        mockMvc.perform(post("/bot123:ABC/getChat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"-1001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.linked_chat_id").exists());
    }

    @Test
    void maxDeleteCommentNotRestorableAndAccessDenied() throws Exception {
        mockMvc.perform(
                        put("/_control/comments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"items":[{"network":"max","ownerId":"chan","commentId":"c1","type":"channel_comment"}]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/max/messages/mid-1/comments/c1").param("chat_id", "chan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(moderationStore.findComment(Network.MAX, "chan", "c1"))
                .get()
                .extracting(ModerationStore.EmulatedComment::deleted)
                .isEqualTo(true);

        mockMvc.perform(post("/max/messages/mid-1/comments/c1/restore"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("not.supported"));

        ScenarioConfig config = ScenarioConfig.defaults();
        config.forNetwork(Network.MAX).setForcedError("access.denied");
        scenarioService.update(config);

        mockMvc.perform(delete("/max/messages").param("message_id", "x"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access.denied"));
    }

    @Test
    void resetClearsModerationStores() throws Exception {
        moderationStore.putSuggest("-1", 1L, java.util.Map.of("text", "x"));
        moderationStore.putComment(Network.VK, "-1", "1", "wall", "c", null, false);
        moderationStore.ban(Network.VK, "1", "2", java.util.Map.of());

        mockMvc.perform(post("/_control/reset")).andExpect(status().isOk());

        assertThat(moderationStore.listSuggests("-1")).isEmpty();
        assertThat(moderationStore.listComments(null, null)).isEmpty();
        assertThat(moderationStore.snapshotBans().get("count")).isEqualTo(0);
    }
}
