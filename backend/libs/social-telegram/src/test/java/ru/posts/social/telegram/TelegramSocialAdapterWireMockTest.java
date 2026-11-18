package ru.posts.social.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.ErrorClass;
import ru.posts.social.api.MediaAssetRef;
import ru.posts.social.api.Network;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.PublishResult;
import ru.posts.social.api.RemoteMediaRef;
import ru.posts.social.api.SocialApiException;

class TelegramSocialAdapterWireMockTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private TelegramSocialAdapter adapter;
    private ChannelContext ctx;

    @BeforeEach
    void setUp() {
        TelegramAdapterProperties properties = new TelegramAdapterProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setReadTimeout(Duration.ofSeconds(5));
        adapter = new TelegramSocialAdapter(properties, new ObjectMapper());
        ctx = new ChannelContext(UUID.randomUUID(), UUID.randomUUID(), "-1001", "bot-token", Network.TG);
    }

    @Test
    void publishSuccess() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/botbot-token/sendMessage"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"ok\":true,\"result\":{\"message_id\":77}}")));

        PublishResult result = adapter.publish(ctx, new PostVariantContent("hi"), "idem-1");

        assertThat(result.remotePostId()).isEqualTo("77");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/botbot-token/sendMessage")));
    }

    @Test
    void http5xxIsRetryable() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/botbot-token/sendMessage"))
                        .willReturn(aResponse()
                                .withStatus(500)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"ok\":false}")));

        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(ex -> {
                    assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.RETRYABLE);
                    assertThat(adapter.classifyError(ex)).isEqualTo(ErrorClass.RETRYABLE);
                });
    }

    @Test
    void http429IsRateLimited() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/botbot-token/sendMessage"))
                        .willReturn(aResponse()
                                .withStatus(429)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"ok\":false,\"parameters\":{\"retry_after\":3}}")));

        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.RATE_LIMITED));
    }

    @Test
    void http401IsAuth() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/botbot-token/sendMessage"))
                        .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.AUTH));
    }

    @Test
    void http403IsAuth() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/botbot-token/sendMessage"))
                        .willReturn(aResponse().withStatus(403)));

        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.AUTH));
    }

    @Test
    void timeoutIsRetryableOrUnknown() {
        TelegramAdapterProperties shortTimeout = new TelegramAdapterProperties();
        shortTimeout.setBaseUrl(wireMock.baseUrl());
        shortTimeout.setReadTimeout(Duration.ofMillis(200));
        TelegramSocialAdapter slowAdapter = new TelegramSocialAdapter(shortTimeout, new ObjectMapper());
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
                        urlPathEqualTo("/botbot-token/sendMessage"))
                .willReturn(aResponse().withFixedDelay(800).withStatus(200).withBody("{\"ok\":true}")));

        assertThatThrownBy(() -> slowAdapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(ex -> assertThat(((SocialApiException) ex).errorClass())
                        .isIn(ErrorClass.RETRYABLE, ErrorClass.UNKNOWN_OUTCOME));
    }

    @Test
    void uploadMediaAndDelete() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/botbot-token/sendPhoto"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":{\"message_id\":9}}")));
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/botbot-token/deleteMessage"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"ok\":true,\"result\":true}")));

        RemoteMediaRef media = adapter.uploadMedia(
                ctx,
                new MediaAssetRef("asset-1", "photo", "image/jpeg", 10, "abc").withContentBytes(new byte[] {1, 2}));
        assertThat(media.attachmentId()).isEqualTo("9");

        adapter.delete(ctx, "9");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/botbot-token/deleteMessage")));
    }

    @Test
    void publishAlbumUsesSendMediaGroup() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/botbot-token/sendMediaGroup"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"ok\":true,\"result\":[{\"message_id\":11,\"photo\":[{\"file_id\":\"f1\"}]},{\"message_id\":12,\"photo\":[{\"file_id\":\"f2\"}]}]}")));

        MediaAssetRef a = new MediaAssetRef("a1", "image", "image/jpeg", 2, "s1").withContentBytes(new byte[] {1});
        MediaAssetRef b = new MediaAssetRef("a2", "image", "image/jpeg", 2, "s2").withContentBytes(new byte[] {2});
        PostVariantContent content = new PostVariantContent("album")
                .withMeta(java.util.Map.of(ru.posts.social.api.PostVariantMeta.MEDIA, java.util.List.of(a, b)));

        PublishResult result = adapter.publish(ctx, content, "album-key");
        assertThat(result.remotePostId()).isEqualTo("11");
        assertThat(result.uploadedMediaByAssetId()).containsKeys("a1", "a2");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/botbot-token/sendMediaGroup")));
    }
}
