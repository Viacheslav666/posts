package ru.posts.social.ok;

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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.ErrorClass;
import ru.posts.social.api.Network;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.PublishResult;
import ru.posts.social.api.SocialApiException;

class OkSocialAdapterWireMockTest {

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
    void publishSuccess() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/ok/fb.do"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"topic-9\",\"success\":true}")));

        PublishResult result = adapter.publish(ctx, new PostVariantContent("hello"), "idem-ok");

        assertThat(result.remotePostId()).isEqualTo("topic-9");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/ok/fb.do"))
                .withRequestBody(containing("method=mediatopic.post"))
                .withRequestBody(containing("idempotency_key=idem-ok")));
    }

    @Test
    void http5xxIsRetryable() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/ok/fb.do"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error_code\":1}")));

        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(ex -> {
                    assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.RETRYABLE);
                    assertThat(adapter.classifyError(ex)).isEqualTo(ErrorClass.RETRYABLE);
                });
    }

    @Test
    void http429IsRateLimited() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/ok/fb.do"))
                .willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.RATE_LIMITED));
    }

    @Test
    void http401And403AreAuth() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/ok/fb.do"))
                .willReturn(aResponse().withStatus(401)));
        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.AUTH));

        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/ok/fb.do"))
                .willReturn(aResponse().withStatus(403)));
        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.AUTH));
    }

    @Test
    void timeoutIsUnknownOrRetryable() {
        OkAdapterProperties shortTimeout = new OkAdapterProperties();
        shortTimeout.setBaseUrl(wireMock.baseUrl());
        shortTimeout.setReadTimeout(Duration.ofMillis(200));
        OkSocialAdapter slowAdapter = new OkSocialAdapter(shortTimeout, new ObjectMapper());
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/ok/fb.do"))
                .willReturn(aResponse().withFixedDelay(800).withStatus(200).withBody("{}")));

        assertThatThrownBy(() -> slowAdapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(ex -> assertThat(((SocialApiException) ex).errorClass())
                        .isIn(ErrorClass.UNKNOWN_OUTCOME, ErrorClass.RETRYABLE));
    }

    @Test
    void deleteIsIdempotentOn404() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/ok/fb.do"))
                .willReturn(aResponse().withStatus(404)));

        adapter.delete(ctx, "missing");
    }
}
