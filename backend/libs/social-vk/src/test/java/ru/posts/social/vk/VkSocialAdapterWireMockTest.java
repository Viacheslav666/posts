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
import ru.posts.social.api.TokenRefreshRequest;

class VkSocialAdapterWireMockTest {

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
        properties.setTokenUrl(wireMock.baseUrl() + "/oauth2/auth");
        properties.setReadTimeout(Duration.ofSeconds(5));
        adapter = new VkSocialAdapter(properties, new ObjectMapper());
        ctx = new ChannelContext(UUID.randomUUID(), UUID.randomUUID(), "-100", "token", Network.VK);
    }

    @Test
    void publishSuccess() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.post"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response\":{\"post_id\":42}}")));

        PublishResult result = adapter.publish(ctx, new PostVariantContent("hello"), "guid-1");

        assertThat(result.remotePostId()).isEqualTo("42");
        assertThat(result.remoteUrl()).contains("wall-100_42");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/wall.post"))
                .withRequestBody(containing("guid=guid-1"))
                .withRequestBody(containing("owner_id=-100")));
    }

    @Test
    void http5xxIsRetryable() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.post"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"server\"}")));

        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.RETRYABLE));
        assertThat(adapter.classifyError(new SocialApiException(ErrorClass.RETRYABLE, "x")))
                .isEqualTo(ErrorClass.RETRYABLE);
    }

    @Test
    void floodControlIsRateLimited() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.post"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"error_code\":9,\"error_msg\":\"Flood control\"}}")));

        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.RATE_LIMITED));
    }

    @Test
    void http429IsRateLimited() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.post"))
                .willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.RATE_LIMITED));
    }

    @Test
    void authErrors() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.post"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"error_code\":5,\"error_msg\":\"User authorization failed\"}}")));

        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.AUTH));

        assertThat(adapter.classifyError(new SocialApiException(ErrorClass.AUTH, "auth", 401, null, null)))
                .isEqualTo(ErrorClass.AUTH);
    }

    @Test
    void http403IsAuth() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.post"))
                .willReturn(aResponse().withStatus(403)));

        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.AUTH));
    }

    @Test
    void timeoutIsUnknownOutcome() {
        VkAdapterProperties shortTimeout = new VkAdapterProperties();
        shortTimeout.setBaseUrl(wireMock.baseUrl());
        shortTimeout.setReadTimeout(Duration.ofMillis(200));
        VkSocialAdapter slowAdapter = new VkSocialAdapter(shortTimeout, new ObjectMapper());
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.post"))
                .willReturn(aResponse().withFixedDelay(800).withStatus(200).withBody("{}")));

        assertThatThrownBy(() -> slowAdapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(ex -> {
                    ErrorClass clazz = ((SocialApiException) ex).errorClass();
                    assertThat(clazz).isIn(ErrorClass.UNKNOWN_OUTCOME, ErrorClass.RETRYABLE);
                    assertThat(slowAdapter.classifyError(ex)).isEqualTo(clazz);
                });
    }

    @Test
    void classifyErrorForHttpStatuses() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.post"))
                .willReturn(aResponse().withStatus(500)));
        try {
            adapter.publish(ctx, new PostVariantContent("x"), "g");
        } catch (SocialApiException ex) {
            assertThat(adapter.classifyError(ex)).isEqualTo(ErrorClass.RETRYABLE);
        }

        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.post"))
                .willReturn(aResponse().withStatus(401)));
        try {
            adapter.publish(ctx, new PostVariantContent("x"), "g");
        } catch (SocialApiException ex) {
            assertThat(adapter.classifyError(ex)).isEqualTo(ErrorClass.AUTH);
        }
    }

    @Test
    void refreshTokenSuccess() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/oauth2/auth"))
                        .withRequestBody(containing("grant_type=refresh_token"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"expires_in\":3600}")));

        var tokens = adapter.refreshToken(new TokenRefreshRequest("old-refresh", "vk", "user-1"));

        assertThat(tokens).isPresent();
        assertThat(tokens.get().accessToken()).isEqualTo("new-access");
        assertThat(tokens.get().refreshToken()).isEqualTo("new-refresh");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/oauth2/auth"))
                .withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=old-refresh")));
    }
}
