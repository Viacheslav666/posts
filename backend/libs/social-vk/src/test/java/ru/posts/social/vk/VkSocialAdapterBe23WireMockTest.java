package ru.posts.social.vk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.ErrorClass;
import ru.posts.social.api.MediaAssetRef;
import ru.posts.social.api.Network;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.PostVariantMeta;
import ru.posts.social.api.PublishResult;
import ru.posts.social.api.RemoteMediaRef;
import ru.posts.social.api.SocialApiException;

/** BE-23 contract tests against VK API 5.199-shaped WireMock fixtures. */
class VkSocialAdapterBe23WireMockTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private VkSocialAdapter adapter;
    private ChannelContext ctx;

    @BeforeEach
    void setUp() {
        VkAdapterProperties properties = new VkAdapterProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setReadTimeout(Duration.ofSeconds(5));
        properties.setReconciliationMode("api");
        adapter = new VkSocialAdapter(properties, new ObjectMapper());
        ctx = new ChannelContext(UUID.randomUUID(), UUID.randomUUID(), "-100", "token", Network.VK);
    }

    @Test
    void publishSendsOptionsAndAttachments() {
        stubWallPost();
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.closeComments"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"response\":1}")));
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("signed", true);
        options.put("mute", true);
        options.put("closeComments", true);
        options.put("copyrightUrl", "https://example.com/src");
        options.put("donutDays", 3);
        options.put("primaryAttachmentsMode", "carousel");
        options.put("geo", Map.of("lat", 55.75, "long", 37.61));
        Map<String, Object> meta = Map.of(
                PostVariantMeta.OPTIONS,
                options,
                PostVariantMeta.ATTACHMENTS,
                List.of("photo-100_1", "photo-100_2", "photo-100_3", "video-100_9"));

        PublishResult result =
                adapter.publish(ctx, new PostVariantContent("hello", null, null, null, null, meta), "guid-opts");

        assertThat(result.remotePostId()).isEqualTo("42");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/wall.post"))
                .withRequestBody(containing("from_group=1"))
                .withRequestBody(containing("signed=1"))
                .withRequestBody(containing("mute_notifications=1"))
                .withRequestBody(containing("close_comments=1"))
                .withRequestBody(containing("copyright="))
                .withRequestBody(containing("donut_paid_duration=3"))
                .withRequestBody(containing("primary_attachments_mode=carousel"))
                .withRequestBody(containing("lat=55.75"))
                .withRequestBody(containing("attachments="))
                .withRequestBody(containing("guid=guid-opts")));
        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/wall.closeComments"))
                .withRequestBody(containing("post_id=42"))
                .withRequestBody(containing("owner_id=-100")));
    }

    @Test
    void publishSendsActionButton() {
        stubWallPost();
        Map<String, Object> options =
                Map.of("actionButton", Map.of("type", "open_url", "title", "Открыть", "url", "https://example.com/a"));
        Map<String, Object> meta = Map.of(PostVariantMeta.OPTIONS, options);

        adapter.publish(ctx, new PostVariantContent("hello", null, null, null, null, meta), "guid-btn");

        assertThat(adapter.capabilities().features().get("actionButton")).isTrue();
        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/wall.post"))
                .withRequestBody(containing("action_button="))
                .withRequestBody(containing("open_url"))
                .withRequestBody(containing("%22action%22")));
    }

    @Test
    void commentSendsScheduledActionIdAsGuid() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.createComment"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"response\":{\"comment_id\":9}}")));

        adapter.comment(ctx, "42", "first", List.of("photo-100_7"), "action-guid-1");

        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/wall.createComment"))
                .withRequestBody(containing("guid=action-guid-1"))
                .withRequestBody(containing("photo-100_7")));
    }

    @Test
    void commentSendsAttachmentsFromGroup() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.createComment"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"response\":{\"comment_id\":9}}")));

        adapter.comment(ctx, "42", "first", List.of("photo-100_7", "video-100_8"));

        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/wall.createComment"))
                .withRequestBody(containing("from_group=100"))
                .withRequestBody(containing("post_id=42"))
                .withRequestBody(containing("attachments="))
                .withRequestBody(containing("photo-100_7")));
    }

    @Test
    void publishWithPollCreatesPollAttachment() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/polls.create"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response\":{\"id\":5,\"owner_id\":-100,\"question\":\"Q?\"}}")));
        stubWallPost();
        Map<String, Object> options = Map.of("poll", Map.of("question", "Q?", "answers", List.of("A", "B")));
        Map<String, Object> meta = Map.of(PostVariantMeta.OPTIONS, options);

        adapter.publish(ctx, new PostVariantContent("poll post", null, null, null, null, meta), "guid-poll");

        wireMock.verify(
                postRequestedFor(urlPathEqualTo("/method/polls.create")).withRequestBody(containing("question=Q")));
        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/wall.post"))
                .withRequestBody(containing("attachments=poll-100_5")));
    }

    @Test
    void uploadPhotoUsesRealBytes() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
                        urlPathEqualTo("/method/photos.getWallUploadServer"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response\":{\"upload_url\":\"" + wireMock.baseUrl()
                                + "/upload\",\"album_id\":-1}}")));
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/upload"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"server\":1,\"photo\":\"[x]\",\"hash\":\"h\"}")));
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/photos.saveWallPhoto"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBodyFile("does-not-matter")
                                        .withBody(
                                                "{\"response\":[{\"id\":7,\"album_id\":-7,\"owner_id\":-100,\"text\":\"\",\"access_key\":\"ab\"}]}")));

        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        MediaAssetRef asset = new MediaAssetRef("a1", "image", "image/jpeg", jpeg.length, "sha", null, jpeg);
        RemoteMediaRef remote = adapter.uploadMedia(ctx, asset);

        assertThat(remote.attachmentId()).isEqualTo("photo-100_7");
        assertThat(remote.kind()).isEqualTo("photo");
    }

    @Test
    void pinAndCommentCallOfficialMethods() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.pin"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response\":1}")));
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.unpin"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response\":1}")));
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.createComment"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"response\":{\"comment_id\":9}}")));

        adapter.pin(ctx, "42", true);
        adapter.pin(ctx, "42", false);
        adapter.comment(ctx, "42", "first!");

        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/wall.pin")).withRequestBody(containing("post_id=42")));
        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/wall.unpin")));
        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/wall.createComment"))
                .withRequestBody(containing("message=first")));
    }

    @Test
    void findExistingViaWallGetPreventsDuplicate() {
        long now = System.currentTimeMillis() / 1000L;
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.get"))
                .withRequestBody(containing("filter=all"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response\":{\"count\":1,\"items\":[{\"id\":42,\"owner_id\":-100,\"date\":"
                                + now
                                + ",\"text\":\"hello from posts\"}]}}")));
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.get"))
                .withRequestBody(containing("filter=postponed"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response\":{\"count\":0,\"items\":[]}}")));

        Optional<PublishResult> found = adapter.findExisting(ctx, "idem-1", new PostVariantContent("hello from posts"));

        assertThat(found).isPresent();
        assertThat(found.get().remotePostId()).isEqualTo("42");
        // Must never hit wall.post when reconciling.
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/method/wall.post")));
    }

    @Test
    void captchaIsPermanent() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.post"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"error_code\":14,\"error_msg\":\"Captcha needed\"}}")));

        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.PERMANENT));
    }

    @Test
    void coOwnersRejectedWithClearError() {
        Map<String, Object> meta = Map.of(PostVariantMeta.OPTIONS, Map.of("coOwnerIds", List.of("1", "2")));
        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x", null, null, null, null, meta), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(ex -> {
                    assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.PERMANENT);
                    assertThat(ex.getMessage()).contains("co-owners");
                });
        assertThat(adapter.capabilities().features().get("coOwners")).isFalse();
    }

    @Test
    void controlPathNotUsedInApiMode() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.get"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response\":{\"count\":0,\"items\":[]}}")));

        adapter.findExisting(ctx, "idem", new PostVariantContent("nope"));

        wireMock.verify(
                0,
                com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                        com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo("/_control/posts")));
    }

    private void stubWallPost() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.post"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response\":{\"post_id\":42}}")));
    }
}
