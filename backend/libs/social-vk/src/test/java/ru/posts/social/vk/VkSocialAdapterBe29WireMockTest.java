package ru.posts.social.vk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.ErrorClass;
import ru.posts.social.api.MediaAssetRef;
import ru.posts.social.api.Network;
import ru.posts.social.api.RemoteMediaRef;
import ru.posts.social.api.SocialApiException;

/** BE-29: clips (shortVideo) + birthday members WireMock contracts. */
class VkSocialAdapterBe29WireMockTest {

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
    void clipsUnavailableReturnsClearError() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/shortVideo.create"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"error\":{\"error_code\":3,\"error_msg\":\"Unknown method passed\"}}")));

        MediaAssetRef asset = new MediaAssetRef(
                UUID.randomUUID().toString(), "clip", "video/mp4", 4, "sha", null, new byte[] {1, 2, 3, 4});
        assertThatThrownBy(() -> adapter.uploadMedia(ctx, asset))
                .isInstanceOf(SocialApiException.class)
                .satisfies(ex -> {
                    assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.PERMANENT);
                    assertThat(ex.getMessage()).contains("vk_clips_unavailable");
                });
        assertThat(adapter.capabilities().features().get("clips")).isTrue();
    }

    @Test
    void birthdaysForbiddenReturnsClearError() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/groups.getMembers"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"error\":{\"error_code\":15,\"error_msg\":\"Access denied: no access to this group\"}}")));

        assertThatThrownBy(() -> adapter.resolveBirthdays(ctx, Instant.parse("2026-11-18T10:00:00Z"), "Europe/Moscow"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(ex -> {
                    assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.PERMANENT);
                    assertThat(ex.getMessage()).contains("vk_birthdays_forbidden");
                });
    }

    @Test
    void birthdaysFiltersByBdate() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/groups.getMembers"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {"response":{"count":2,"items":[
                                  {"id":1,"first_name":"Иван","last_name":"Иванов","bdate":"18.11.1990"},
                                  {"id":2,"first_name":"Пётр","last_name":"Петров","bdate":"19.11"}
                                ]}}
                                """)));

        String names = adapter.resolveBirthdays(ctx, Instant.parse("2026-11-18T10:00:00Z"), "Europe/Moscow");
        assertThat(names).isEqualTo("Иван Иванов");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/groups.getMembers")));
    }

    @Test
    void clipUploadHappyPath() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/shortVideo.create"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"response\":{\"upload_url\":\"" + wireMock.baseUrl()
                                        + "/upload\",\"video_id\":55}}")));
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/upload"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"owner_id\":-100,\"video_id\":55}")));
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/shortVideo.publish"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"response\":{\"video\":{\"owner_id\":-100,\"video_id\":55}}}")));

        MediaAssetRef asset = new MediaAssetRef(
                UUID.randomUUID().toString(), "clip", "video/mp4", 4, "sha", null, new byte[] {1, 2, 3, 4});
        RemoteMediaRef remote = adapter.uploadMedia(ctx, asset);
        assertThat(remote.attachmentId()).isEqualTo("video-100_55");
        assertThat(remote.kind()).isEqualTo("clip");
    }
}
