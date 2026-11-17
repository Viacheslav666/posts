package ru.posts.social.max;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class MaxSocialAdapterWireMockTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private MaxSocialAdapter adapter;
    private ChannelContext ctx;

    @BeforeEach
    void setUp() {
        MaxAdapterProperties properties = new MaxAdapterProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setReadTimeout(Duration.ofSeconds(5));
        adapter = new MaxSocialAdapter(properties);
        ctx = new ChannelContext(UUID.randomUUID(), UUID.randomUUID(), "chat-1", "max-token", Network.MAX);
    }

    @Test
    void publishSuccess() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/max/messages"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"message\":{\"body\":{\"mid\":\"m-1\",\"text\":\"hi\"},\"recipient\":{\"chat_id\":\"chat-1\"}}}")));

        PublishResult result = adapter.publish(ctx, new PostVariantContent("hi"), "idem-max");

        assertThat(result.remotePostId()).isEqualTo("m-1");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/max/messages"))
                .withHeader("Authorization", equalTo("Bearer max-token")));
    }

    @Test
    void http5xxIsRetryable() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/max/messages"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"server\"}")));

        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(ex -> {
                    assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.RETRYABLE);
                    assertThat(adapter.classifyError(ex)).isEqualTo(ErrorClass.RETRYABLE);
                });
    }

    @Test
    void http429IsRateLimited() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/max/messages"))
                .willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.RATE_LIMITED));
    }

    @Test
    void http401And403AreAuth() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/max/messages"))
                .willReturn(aResponse().withStatus(401)));
        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.AUTH));

        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/max/messages"))
                .willReturn(aResponse().withStatus(403)));
        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.AUTH));
    }

    @Test
    void timeoutIsUnknownOrRetryable() {
        MaxAdapterProperties shortTimeout = new MaxAdapterProperties();
        shortTimeout.setBaseUrl(wireMock.baseUrl());
        shortTimeout.setReadTimeout(Duration.ofMillis(200));
        MaxSocialAdapter slowAdapter = new MaxSocialAdapter(shortTimeout);
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/max/messages"))
                .willReturn(aResponse().withFixedDelay(800).withStatus(200).withBody("{}")));

        assertThatThrownBy(() -> slowAdapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(ex -> assertThat(((SocialApiException) ex).errorClass())
                        .isIn(ErrorClass.UNKNOWN_OUTCOME, ErrorClass.RETRYABLE));
    }

    @Test
    void deleteIgnores404() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.delete(urlPathEqualTo("/max/messages/missing"))
                .willReturn(aResponse().withStatus(404)));

        adapter.delete(ctx, "missing");
    }
}
