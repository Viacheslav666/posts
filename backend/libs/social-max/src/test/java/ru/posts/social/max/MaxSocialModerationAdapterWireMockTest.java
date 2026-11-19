package ru.posts.social.max;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
import ru.posts.social.api.ModerationFeatureFlags;
import ru.posts.social.api.ModerationResult;
import ru.posts.social.api.ModerationTarget;
import ru.posts.social.api.Network;
import ru.posts.social.api.SocialApiException;

class MaxSocialModerationAdapterWireMockTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private MaxSocialModerationAdapter adapter;
    private ChannelContext ctx;

    @BeforeEach
    void setUp() {
        MaxAdapterProperties properties = new MaxAdapterProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setReadTimeout(Duration.ofSeconds(5));
        adapter = new MaxSocialModerationAdapter(properties, new ObjectMapper());
        ctx = new ChannelContext(UUID.randomUUID(), UUID.randomUUID(), "chat-1", "max-token", Network.MAX);
    }

    @Test
    void deleteCommentSuccess() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.delete(urlPathEqualTo("/max/messages/mid-1/comments"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"success\":true}")));

        ModerationResult result =
                adapter.deleteComment(ctx, new ModerationTarget("channel_comment", "mid-1", "c-9", null, null));

        assertThat(result.remoteId()).isEqualTo("c-9");
        wireMock.verify(deleteRequestedFor(urlPathEqualTo("/max/messages/mid-1/comments"))
                .withQueryParam("comment_id", equalTo("c-9"))
                .withHeader("Authorization", equalTo("Bearer max-token")));
    }

    @Test
    void accessDeniedIsPermission() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.delete(urlPathEqualTo("/max/messages/mid-1/comments"))
                        .willReturn(aResponse()
                                .withStatus(403)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"code\":\"access.denied\",\"message\":\"access.denied\"}")));

        assertThatThrownBy(() ->
                        adapter.deleteComment(ctx, new ModerationTarget("channel_comment", "mid-1", "c-9", null, null)))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.PERMISSION));

        assertThat(adapter.moderationCapabilities().features().get(ModerationFeatureFlags.RESTORE_COMMENT))
                .isFalse();
        assertThat(adapter.moderationCapabilities()
                        .features()
                        .get(ModerationFeatureFlags.DELETE_COMMENT_CHANNEL_COMMENT))
                .isTrue();
    }
}
