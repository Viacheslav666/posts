package ru.posts.social.vk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.ErrorClass;
import ru.posts.social.api.Network;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.PostVariantMeta;
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
                .withRequestBody(containing("owner_id=-100"))
                .withRequestBody(containing("from_group=1")));
    }

    @Test
    void personalProfileDoesNotSendFromGroup() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.post"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response\":{\"post_id\":9}}")));
        ChannelContext profile = new ChannelContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "1001",
                "classic-user-token",
                Network.VK,
                null,
                "profile",
                "active");

        adapter.publish(profile, new PostVariantContent("hello"), "guid-profile");

        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/wall.post"))
                .withRequestBody(containing("owner_id=1001"))
                .withRequestBody(notContaining("from_group=")));
    }

    @Test
    void vkIdTokenOnProfileIsBlockedBeforeWallPost() {
        ChannelContext profile = new ChannelContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "1001",
                "vk2.a.profile-token",
                Network.VK,
                null,
                "profile",
                "active");

        assertThatThrownBy(() -> adapter.publish(profile, new PostVariantContent("hello"), "guid-blocked"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(ex -> {
                    SocialApiException api = (SocialApiException) ex;
                    assertThat(api.errorClass()).isEqualTo(ErrorClass.AUTH);
                    assertThat(api.providerCode()).contains("1051");
                    assertThat(api.getMessage()).isEqualTo(VkWallPostGate.BLOCK_MESSAGE);
                });
        wireMock.verify(exactly(0), postRequestedFor(urlPathEqualTo("/method/wall.post")));
    }

    @Test
    void error1051IsAuthNotPermanent() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.post"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"error\":{\"error_code\":1051,\"error_msg\":\"Method is not available for this profile type\"}}")));

        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(ex -> {
                    SocialApiException api = (SocialApiException) ex;
                    assertThat(api.errorClass()).isEqualTo(ErrorClass.AUTH);
                    assertThat(api.providerCode()).contains("1051");
                    assertThat(api.errorClass()).isNotEqualTo(ErrorClass.PERMANENT);
                });
    }

    @Test
    void publishDateIsSentForPostponedGrabberPosts() {
        long publishDate = Instant.now().plusSeconds(3600).getEpochSecond();
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.post"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response\":{\"post_id\":7}}")));

        PostVariantContent content = new PostVariantContent(
                "later", null, null, null, null, Map.of(PostVariantMeta.OPTIONS, Map.of("vkPublishDate", publishDate)));
        PublishResult result = adapter.publish(ctx, content, "guid-later");

        assertThat(result.remotePostId()).isEqualTo("7");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/wall.post"))
                .withRequestBody(containing("publish_date=" + publishDate)));
    }

    @Test
    void wallRepostUsesObjectAndGroup() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.repost"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response\":{\"success\":1,\"post_id\":11}}")));

        PostVariantContent content = new PostVariantContent(
                "comment",
                null,
                null,
                null,
                null,
                Map.of(
                        PostVariantMeta.OPTIONS,
                        Map.of(
                                "vkPublishDate",
                                Instant.now().plusSeconds(3600).getEpochSecond(),
                                "repost",
                                Map.of("object", "wall-5_9", "message", "comment"))));
        PublishResult result = adapter.publish(ctx, content, "guid-repost");

        assertThat(result.remotePostId()).isEqualTo("11");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/wall.repost"))
                .withRequestBody(containing("object=wall-5_9"))
                .withRequestBody(containing("group_id=100"))
                .withRequestBody(containing("message=comment"))
                .withRequestBody(notContaining("guid="))
                .withRequestBody(notContaining("publish_date")));
    }

    @Test
    void repostRetryMatchesPublishedCopyHistory() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.get"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"response\":{\"items\":[{\"id\":11,\"text\":\"comment\",\"copy_history\":[{\"owner_id\":-5,\"id\":9}]}]}}")));
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.repost"))
                .willReturn(aResponse().withStatus(500)));

        PostVariantContent content = new PostVariantContent(
                "comment",
                null,
                null,
                null,
                null,
                Map.of(PostVariantMeta.OPTIONS, Map.of("repost", Map.of("object", "wall-5_9", "message", "comment"))));
        PublishResult result = adapter.publish(ctx, content, "guid-repost");

        assertThat(result.remotePostId()).isEqualTo("11");
        wireMock.verify(exactly(0), postRequestedFor(urlPathEqualTo("/method/wall.repost")));
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
                .satisfies(ex -> {
                    SocialApiException api = (SocialApiException) ex;
                    assertThat(api.errorClass()).isEqualTo(ErrorClass.RATE_LIMITED);
                    assertThat(api.retryAfter()).contains(Duration.ofHours(1));
                });
    }

    @Test
    void tooManyRequestsPerSecondBacksOffBriefly() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.post"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"error_code\":6,\"error_msg\":\"Too many requests per second\"}}")));

        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(ex -> {
                    SocialApiException api = (SocialApiException) ex;
                    assertThat(api.errorClass()).isEqualTo(ErrorClass.RATE_LIMITED);
                    assertThat(api.retryAfter()).contains(Duration.ofSeconds(5));
                });
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
                                                "{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"expires_in\":3600,\"state\":\"state-32-chars-aaaaaaaaaaaa\"}")));

        var tokens = adapter.refreshToken(new TokenRefreshRequest(
                "old-refresh", "vk", "user-1", "54917886", "device-1", "state-32-chars-aaaaaaaaaaaa"));

        assertThat(tokens).isPresent();
        assertThat(tokens.get().accessToken()).isEqualTo("new-access");
        assertThat(tokens.get().refreshToken()).isEqualTo("new-refresh");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/oauth2/auth"))
                .withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=old-refresh"))
                .withRequestBody(containing("client_id=54917886"))
                .withRequestBody(containing("device_id=device-1"))
                .withRequestBody(containing("state=state-32-chars-aaaaaaaaaaaa")));
    }
}
