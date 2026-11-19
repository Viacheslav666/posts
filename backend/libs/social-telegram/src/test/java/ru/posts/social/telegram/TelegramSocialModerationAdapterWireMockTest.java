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
import ru.posts.social.api.ModerationFeatureFlags;
import ru.posts.social.api.ModerationResult;
import ru.posts.social.api.ModerationTarget;
import ru.posts.social.api.Network;
import ru.posts.social.api.SocialApiException;

class TelegramSocialModerationAdapterWireMockTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private TelegramSocialModerationAdapter adapter;
    private ChannelContext ctx;

    @BeforeEach
    void setUp() {
        TelegramAdapterProperties properties = new TelegramAdapterProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setReadTimeout(Duration.ofSeconds(5));
        adapter = new TelegramSocialModerationAdapter(properties, new ObjectMapper());
        ctx = new ChannelContext(UUID.randomUUID(), UUID.randomUUID(), "-1001", "bot-token", Network.TG);
    }

    @Test
    void deleteMessageSuccess() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/botbot-token/deleteMessage"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"ok\":true,\"result\":true}")));

        ModerationResult result =
                adapter.deletePost(ctx, new ModerationTarget("channel_comment", "77", null, null, null));

        assertThat(result.remoteId()).isEqualTo("77");
        assertThat(result.alreadyGone()).isFalse();
        wireMock.verify(postRequestedFor(urlPathEqualTo("/botbot-token/deleteMessage")));
    }

    @Test
    void restoreCommentUnsupported() {
        assertThatThrownBy(() ->
                        adapter.restoreComment(ctx, new ModerationTarget("channel_comment", null, "77", null, null)))
                .isInstanceOf(SocialApiException.class)
                .satisfies(ex -> {
                    assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.PERMANENT);
                    assertThat(ex.getMessage()).containsIgnoringCase("restoreComment");
                });
        assertThat(adapter.moderationCapabilities().features().get(ModerationFeatureFlags.RESTORE_COMMENT))
                .isFalse();
        assertThat(adapter.moderationCapabilities().features().get(ModerationFeatureFlags.PUBLISH_SUGGESTED))
                .isFalse();
        assertThat(adapter.moderationCapabilities()
                        .features()
                        .get(ModerationFeatureFlags.DELETE_COMMENT_CHANNEL_COMMENT))
                .isTrue();
    }
}
