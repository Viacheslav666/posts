package ru.posts.grabber.media;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.config.GrabberProperties;
import ru.posts.grabber.ssrf.HostResolver;
import ru.posts.grabber.ssrf.SafeHttpFetcher;
import ru.posts.grabber.ssrf.SsrfProperties;
import ru.posts.grabber.token.GrabberTokenProvider;
import ru.posts.persistence.entity.AutomationRuleEntity;
import ru.posts.persistence.entity.ChannelEntity;

class GrabberMediaResolverTest {

    @RegisterExtension
    static WireMockExtension vk = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private GrabberProperties properties;
    private GrabberMediaResolver resolver;
    private ChannelEntity channel;

    @BeforeEach
    void setUp() {
        SsrfProperties ssrf = new SsrfProperties();
        ssrf.setAllowedHosts(List.of("localhost", "127.0.0.1"));
        VkMediaClient client =
                new VkMediaClient(new SafeHttpFetcher(ssrf, HostResolver.SYSTEM), ssrf, vk.baseUrl(), "5.199");
        GrabberTokenProvider tokens = new GrabberTokenProvider() {
            @Override
            public Optional<String> resolveVkToken(UUID workspaceId, UUID targetChannelId) {
                return Optional.of("read-token");
            }

            @Override
            public Optional<String> resolveTargetUploadToken(UUID workspaceId, UUID targetChannelId) {
                return Optional.of("admin-token");
            }
        };
        properties = new GrabberProperties();
        resolver = new GrabberMediaResolver(client, tokens, properties);
        channel = new ChannelEntity();
        channel.setId(UUID.randomUUID());
        channel.setNetwork("vk");
        channel.setExternalId("-999");

        stubJson("photos.getWallUploadServer", "{\"response\":{\"upload_url\":\"" + vk.baseUrl() + "/upload/photo\"}}");
        vk.stubFor(post(urlPathEqualTo("/upload/photo"))
                .willReturn(json("{\"server\":1,\"photo\":\"[{}]\",\"hash\":\"h\"}")));
        stubJson("photos.saveWallPhoto", "{\"response\":[{\"id\":501,\"owner_id\":-999}]}");
        stubJson(
                "video.save",
                "{\"response\":{\"upload_url\":\"" + vk.baseUrl()
                        + "/upload/video\",\"video_id\":777,\"owner_id\":-999}}");
        vk.stubFor(post(urlPathEqualTo("/upload/video")).willReturn(json("{\"size\":10}")));
        vk.stubFor(get(urlPathEqualTo("/cdn/p.jpg"))
                .willReturn(aResponse().withHeader("Content-Type", "image/jpeg").withBody(new byte[] {1, 2, 3})));
        vk.stubFor(get(urlPathEqualTo("/cdn/v.mp4"))
                .willReturn(aResponse().withHeader("Content-Type", "video/mp4").withBody(new byte[2048])));
    }

    private void stubJson(String method, String body) {
        vk.stubFor(post(urlPathEqualTo("/method/" + method)).willReturn(json(body)));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }

    private AutomationRuleEntity rule(Map<String, Object> transform) {
        AutomationRuleEntity rule = new AutomationRuleEntity();
        rule.setId(UUID.randomUUID());
        rule.setWorkspaceId(UUID.randomUUID());
        rule.setTargetChannelId(channel.getId());
        rule.setConfig(GrabberConfigSupport.withDefaults(Map.of("transform", transform)));
        return rule;
    }

    private Map<String, Object> photo(int id) {
        Map<String, Object> att = new LinkedHashMap<>();
        att.put("type", "photo");
        att.put("owner_id", "-123");
        att.put("item_id", String.valueOf(id));
        att.put("access_key", "ak");
        att.put("url", vk.baseUrl() + "/cdn/p.jpg");
        return att;
    }

    private Map<String, Object> video(int id) {
        Map<String, Object> att = new LinkedHashMap<>();
        att.put("type", "video");
        att.put("owner_id", "-123");
        att.put("item_id", String.valueOf(id));
        att.put("title", "Ролик");
        return att;
    }

    @Test
    void photosAreReuploadedToTargetGroup() {
        GrabberMediaResolver.Resolution r = resolver.resolve(rule(Map.of()), channel, List.of(photo(1)), "t");
        assertThat(r.failed()).isFalse();
        assertThat(r.attachments()).containsExactly("photo-999_501");
        vk.verify(postRequestedFor(urlPathEqualTo("/method/photos.getWallUploadServer"))
                .withRequestBody(containing("group_id=999"))
                .withRequestBody(containing("access_token=admin-token")));
    }

    @Test
    void photoFallsBackToSourceReferenceWhenUploadFails() {
        stubJson("photos.saveWallPhoto", "{\"error\":{\"error_code\":100,\"error_msg\":\"bad\"}}");
        GrabberMediaResolver.Resolution r = resolver.resolve(rule(Map.of()), channel, List.of(photo(1)), "t");
        assertThat(r.failed()).isFalse();
        assertThat(r.attachments()).containsExactly("photo-123_1_ak");
    }

    @Test
    void videoReferencedWhenReuploadDisabled() {
        GrabberMediaResolver.Resolution r = resolver.resolve(rule(Map.of()), channel, List.of(video(5)), "t");
        assertThat(r.attachments()).containsExactly("video-123_5");
        vk.verify(0, postRequestedFor(urlPathEqualTo("/method/video.get")));
    }

    @Test
    void publicVideoReuploadedWhenEnabled() {
        stubJson(
                "video.get",
                "{\"response\":{\"count\":1,\"items\":[{\"id\":5,\"owner_id\":-123,\"title\":\"Ролик\","
                        + "\"files\":{\"mp4_720\":\"" + vk.baseUrl() + "/cdn/v.mp4\"}}]}}");
        GrabberMediaResolver.Resolution r =
                resolver.resolve(rule(Map.of("reupload_video", true)), channel, List.of(video(5)), "t");
        assertThat(r.failed()).isFalse();
        assertThat(r.attachments()).containsExactly("video-999_777");
        vk.verify(postRequestedFor(urlPathEqualTo("/method/video.get"))
                .withRequestBody(containing("videos=-123_5"))
                .withRequestBody(containing("access_token=read-token")));
        vk.verify(postRequestedFor(urlPathEqualTo("/method/video.save")).withRequestBody(containing("group_id=999")));
        vk.verify(postRequestedFor(urlPathEqualTo("/upload/video")));
    }

    @Test
    void privateVideoBlocksPublication() {
        Map<String, Object> priv = video(6);
        priv.put("is_private", true);
        GrabberMediaResolver.Resolution r =
                resolver.resolve(rule(Map.of("reupload_video", true)), channel, List.of(photo(1), priv), "t");
        assertThat(r.failed()).isTrue();
        assertThat(r.failureReason()).isEqualTo("video_reupload_failed");
        assertThat(r.failureCode()).isEqualTo("video_private");
    }

    @Test
    void tooLarge4kFallsBackTo720p() {
        properties.getMedia().setMaxVideoBytes(1500);
        stubJson(
                "video.get",
                "{\"response\":{\"count\":1,\"items\":[{\"id\":5,\"owner_id\":-123,\"title\":\"Ролик\","
                        + "\"files\":{\"mp4_2160\":\"" + vk.baseUrl() + "/cdn/v4k.mp4\",\"mp4_720\":\""
                        + vk.baseUrl() + "/cdn/v720.mp4\"}}]}}");
        vk.stubFor(get(urlPathEqualTo("/cdn/v4k.mp4"))
                .willReturn(aResponse().withHeader("Content-Type", "video/mp4").withBody(new byte[4000])));
        vk.stubFor(get(urlPathEqualTo("/cdn/v720.mp4"))
                .willReturn(aResponse().withHeader("Content-Type", "video/mp4").withBody(new byte[100])));
        GrabberMediaResolver.Resolution r =
                resolver.resolve(rule(Map.of("reupload_video", true)), channel, List.of(video(5)), "t");
        assertThat(r.failed()).isFalse();
        assertThat(r.attachments()).containsExactly("video-999_777");
        vk.verify(getRequestedFor(urlPathEqualTo("/cdn/v720.mp4")));
    }

    @Test
    void privateUploadServerIsRejectedAndPhotoFallsBack() {
        stubJson(
                "photos.getWallUploadServer",
                "{\"response\":{\"upload_url\":\"http://169.254.169.254/latest/meta-data\"}}");
        GrabberMediaResolver.Resolution r = resolver.resolve(rule(Map.of()), channel, List.of(photo(1)), "t");
        assertThat(r.failed()).isFalse();
        assertThat(r.attachments()).containsExactly("photo-123_1_ak");
    }

    @Test
    void signedCdnUrlDoesNotLeakIntoFailureMessage() {
        String signed = vk.baseUrl() + "/cdn/v.mp4?token=supersecret";
        stubJson(
                "video.get",
                "{\"response\":{\"count\":1,\"items\":[{\"id\":5,\"owner_id\":-123," + "\"files\":{\"mp4_720\":\""
                        + signed + "\"}}]}}");
        vk.stubFor(get(urlPathEqualTo("/cdn/v.mp4")).willReturn(aResponse().withStatus(500)));
        GrabberMediaResolver.Resolution r =
                resolver.resolve(rule(Map.of("reupload_video", true)), channel, List.of(video(5)), "t");
        assertThat(r.failed()).isTrue();
        assertThat(r.failureMessage()).doesNotContain("supersecret");
    }

    @Test
    void oversizedVideoBlocksPublication() {
        properties.getMedia().setMaxVideoBytes(1024);
        stubJson(
                "video.get",
                "{\"response\":{\"count\":1,\"items\":[{\"id\":5,\"owner_id\":-123," + "\"files\":{\"mp4_720\":\""
                        + vk.baseUrl() + "/cdn/v.mp4\"}}]}}");
        GrabberMediaResolver.Resolution r =
                resolver.resolve(rule(Map.of("reupload_video", true)), channel, List.of(video(5)), "t");
        assertThat(r.failed()).isTrue();
        assertThat(r.failureCode()).isEqualTo("video_too_large");
        vk.verify(0, postRequestedFor(urlPathEqualTo("/method/video.save")));
    }

    @Test
    void unavailableOrExternalVideoBlocksPublication() {
        stubJson("video.get", "{\"response\":{\"count\":0,\"items\":[]}}");
        assertThat(resolver.resolve(rule(Map.of("reupload_video", true)), channel, List.of(video(5)), "t")
                        .failureCode())
                .isEqualTo("video_unavailable");

        stubJson(
                "video.get",
                "{\"response\":{\"count\":1,\"items\":[{\"id\":5,\"owner_id\":-123,\"platform\":\"YouTube\"}]}}");
        assertThat(resolver.resolve(rule(Map.of("reupload_video", true)), channel, List.of(video(5)), "t")
                        .failureCode())
                .isEqualTo("video_external");
    }

    @Test
    void clipsAreReferencedEvenWithVideoReupload() {
        Map<String, Object> clip = video(8);
        clip.put("is_clip", true);
        GrabberMediaResolver.Resolution r =
                resolver.resolve(rule(Map.of("reupload_video", true)), channel, List.of(clip), "t");
        assertThat(r.attachments()).containsExactly("video-123_8");
    }

    @Test
    void otherTypesReferencedAndOnlyOneLink() {
        List<Map<String, Object>> atts = List.of(
                Map.of("type", "audio", "owner_id", "1", "item_id", "2"),
                Map.of("type", "audio_playlist", "owner_id", "1", "item_id", "3", "access_key", "x"),
                Map.of("type", "doc", "owner_id", "1", "item_id", "4"),
                Map.of("type", "poll", "owner_id", "-123", "item_id", "5"),
                Map.of("type", "album", "owner_id", "-123", "item_id", "6"),
                Map.of("type", "market", "owner_id", "-123", "item_id", "7"),
                Map.of("type", "market_album", "owner_id", "-123", "item_id", "8"),
                Map.of("type", "page", "owner_id", "-123", "item_id", "9"),
                Map.of("type", "link", "url", "https://a.example"),
                Map.of("type", "article", "url", "https://vk.com/@b"));
        GrabberMediaResolver.Resolution r = resolver.resolve(rule(Map.of()), channel, atts, "t");
        assertThat(r.attachments())
                .containsExactly(
                        "audio1_2",
                        "audio_playlist1_3_x",
                        "doc1_4",
                        "poll-123_5",
                        "album-123_6",
                        "market-123_7",
                        "market_album-123_8",
                        "page-123_9",
                        "https://a.example");
    }

    @Test
    void userAttachmentsOnlyWhenPostHasNone() {
        AutomationRuleEntity rule =
                rule(Map.of("user_attachments", "photo-1_1, [keyTextEntry]video-1_2|смеят[/keyTextEntry]"));
        assertThat(resolver.resolve(rule, channel, List.of(), "Все смеятся").attachments())
                .containsExactly("photo-1_1", "video-1_2");
        assertThat(resolver.resolve(rule, channel, List.of(photo(1)), "Все смеятся")
                        .attachments())
                .containsExactly("photo-999_501");
    }

    @Test
    void carouselOnlyWithTwoOrMoreVisualItems() {
        AutomationRuleEntity carousel = rule(Map.of("attachments_mode", "carousel"));
        assertThat(resolver.resolve(carousel, channel, List.of(photo(1), video(2)), "t")
                        .carousel())
                .isTrue();
        assertThat(resolver.resolve(carousel, channel, List.of(photo(1)), "t").carousel())
                .isFalse();
        assertThat(resolver.resolve(rule(Map.of()), channel, List.of(photo(1), video(2)), "t")
                        .carousel())
                .isFalse();
    }

    @Test
    void capsAtTenAttachments() {
        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            many.add(video(i));
        }
        assertThat(resolver.resolve(rule(Map.of()), channel, many, "t").attachments())
                .hasSize(GrabbedAttachments.VK_MAX_ATTACHMENTS);
    }

    @Test
    void nonVkTargetGetsNoVkAttachments() {
        channel.setNetwork("telegram");
        assertThat(resolver.resolve(rule(Map.of()), channel, List.of(photo(1)), "t")
                        .attachments())
                .isEmpty();
        vk.verify(0, getRequestedFor(urlPathEqualTo("/cdn/p.jpg")));
    }
}
