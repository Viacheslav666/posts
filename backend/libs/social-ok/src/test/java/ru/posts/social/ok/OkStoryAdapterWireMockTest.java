package ru.posts.social.ok;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.MediaAssetRef;
import ru.posts.social.api.Network;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.PostVariantMeta;
import ru.posts.social.api.PublishResult;

class OkStoryAdapterWireMockTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private OkSocialAdapter adapter;
    private ChannelContext ctx;

    @BeforeEach
    void setUp() {
        OkAdapterProperties properties = new OkAdapterProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setReadTimeout(Duration.ofSeconds(5));
        adapter = new OkSocialAdapter(properties, new ObjectMapper());
        ctx = new ChannelContext(UUID.randomUUID(), UUID.randomUUID(), "gid-1", "token", Network.OK);
    }

    @Test
    @DisplayName("TC-STORY-02: publishStory Moments getUploadUrl → upload → commit")
    void publishMoment() {
        String uploadPath = "/ok/upload/m1";
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/ok/fb.do"))
                .withRequestBody(containing("moments.getUploadUrl"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"upload_url\":\"" + wireMock.baseUrl() + uploadPath + "\"}")));
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo(uploadPath))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"tok-1\"}")));
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/ok/fb.do"))
                .withRequestBody(containing("moments.commit"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"moment_id\":\"ok-moment-9\",\"success\":true}")));

        byte[] mp4 = new byte[] {0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70};
        MediaAssetRef asset = new MediaAssetRef("m1", "video", "video/mp4", mp4.length, "sha", null, mp4);
        PublishResult result = adapter.publishStory(
                ctx,
                new PostVariantContent(
                        "moment",
                        "video",
                        "ttl_48h",
                        "go_to",
                        "https://ok.ru/group/1",
                        Map.of(PostVariantMeta.MEDIA, List.of(asset))),
                "ok-story-1");

        assertThat(result.remotePostId()).isEqualTo("ok-moment-9");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/ok/fb.do")).withRequestBody(containing("moments.commit")));
        wireMock.verify(postRequestedFor(urlPathEqualTo("/ok/fb.do")).withRequestBody(containing("ttl_ms")));
        wireMock.verify(postRequestedFor(urlPathEqualTo("/ok/fb.do")).withRequestBody(containing("owner_ref")));
        wireMock.verify(postRequestedFor(urlPathEqualTo("/ok/fb.do")).withRequestBody(containing("link_url")));
        wireMock.verify(postRequestedFor(urlPathEqualTo(uploadPath)));
    }

    @Test
    @DisplayName("deleteStory Moments is idempotent")
    void deleteMoment() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/ok/fb.do"))
                .withRequestBody(containing("moments.delete"))
                .willReturn(aResponse().withStatus(200)));
        adapter.deleteStory(ctx, "ok-moment-9");
    }
}
