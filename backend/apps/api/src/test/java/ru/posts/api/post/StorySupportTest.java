package ru.posts.api.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.post.PostDtos;

class StorySupportTest {

    @Test
    @DisplayName("TC-STORY-13: unsupported networks rejected")
    void unsupportedNetworks() {
        assertThatThrownBy(() -> StorySupport.assertSupportedNetworks(List.of(new PostDtos.PostVariantUpsert(
                        null, java.util.UUID.randomUUID(), ApiEnums.Network.tg, null, null))))
                .isInstanceOf(ApiException.class)
                .satisfies(ex ->
                        assertThat(((ApiException) ex).getCode()).isEqualTo(ProblemCodes.STORY_UNSUPPORTED_NETWORK));
    }

    @Test
    @DisplayName("TC-STORY-05: VK external URL invalid")
    void vkLinkValidation() {
        PostDtos.StoryOptions story = new PostDtos.StoryOptions(
                ApiEnums.StoryTtl.ttl_24h, "go_to", "https://example.com", null, null, null, null, null, null, null);
        assertThatThrownBy(() -> StorySupport.assertLink(story, List.of(ApiEnums.Network.vk)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ProblemCodes.STORY_LINK_INVALID));
        assertThat(StorySupport.isVkInternalUrl("https://vk.com/club1")).isTrue();
    }

    @Test
    @DisplayName("TC-STORY-03: OK effective TTL degradation")
    void okEffectiveTtl() {
        PostDtos.StoryOptions story = new PostDtos.StoryOptions(
                ApiEnums.StoryTtl.ttl_48h, null, null, null, null, null, null, null, null, null);
        Map<String, Object> map = StorySupport.toStoryOptionsMap(story, List.of(ApiEnums.Network.ok), "photo");
        assertThat(map.get("ttl")).isEqualTo("ttl_48h");
        assertThat(map.get("effectiveTtl")).isEqualTo("ttl_24h");
        assertThat(map.get("warnings")).asList().isNotEmpty();
    }

    @Test
    @DisplayName("TC-STORY-17/30: bmp rejected; video over 350MB rejected")
    void mediaLimits() {
        ru.posts.persistence.entity.MediaAssetEntity bmp = new ru.posts.persistence.entity.MediaAssetEntity();
        bmp.setMime("image/bmp");
        bmp.setSizeBytes(1024L);
        assertThatThrownBy(() -> StorySupport.assertMedia(
                        new PostDtos.PostContent("", null, List.of(java.util.UUID.randomUUID())), List.of(bmp)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ProblemCodes.STORY_MEDIA_INVALID));

        ru.posts.persistence.entity.MediaAssetEntity huge = new ru.posts.persistence.entity.MediaAssetEntity();
        huge.setMime("video/mp4");
        huge.setSizeBytes(350L * 1024L * 1024L + 1L);
        assertThatThrownBy(() -> StorySupport.assertMedia(
                        new PostDtos.PostContent("", null, List.of(java.util.UUID.randomUUID())), List.of(huge)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ProblemCodes.STORY_MEDIA_INVALID));
    }
}
