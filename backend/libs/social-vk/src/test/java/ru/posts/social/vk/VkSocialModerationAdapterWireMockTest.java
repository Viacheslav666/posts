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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.ErrorClass;
import ru.posts.social.api.ModerationBatchItem;
import ru.posts.social.api.ModerationBatchItemResult;
import ru.posts.social.api.ModerationFeatureFlags;
import ru.posts.social.api.ModerationPayload;
import ru.posts.social.api.ModerationResult;
import ru.posts.social.api.ModerationTarget;
import ru.posts.social.api.Network;
import ru.posts.social.api.SocialApiException;

class VkSocialModerationAdapterWireMockTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private VkSocialModerationAdapter adapter;
    private ChannelContext ctx;

    @BeforeEach
    void setUp() {
        VkAdapterProperties properties = new VkAdapterProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setReadTimeout(Duration.ofSeconds(5));
        adapter = new VkSocialModerationAdapter(properties, new ObjectMapper());
        ctx = new ChannelContext(UUID.randomUUID(), UUID.randomUUID(), "-100", "token", Network.VK);
    }

    @Test
    void publishSuggestedSuccess() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.post"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response\":{\"post_id\":55}}")));

        ModerationResult result = adapter.publishSuggested(
                ctx,
                new ModerationTarget("wall", "12", null, null, "-100", Map.of("guid", "idem-sug")),
                new ModerationPayload(null, true, true, null, null, null, null, null, null, Map.of()));

        assertThat(result.remoteId()).isEqualTo("55");
        assertThat(result.alreadyGone()).isFalse();
        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/wall.post"))
                .withRequestBody(containing("post_id=12"))
                .withRequestBody(containing("from_group=1"))
                .withRequestBody(containing("signed=1"))
                .withRequestBody(containing("guid=idem-sug")));
    }

    @Test
    void publishSuggestedPermissionError15() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.post"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"error_code\":15,\"error_msg\":\"Access denied\"}}")));

        assertThatThrownBy(() -> adapter.publishSuggested(
                        ctx, new ModerationTarget(null, "12", null, null, "-100"), ModerationPayload.empty()))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.PERMISSION));
    }

    @Test
    void deleteCommentAlreadyGone() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.deleteComment"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"error\":{\"error_code\":15,\"error_msg\":\"Access denied: comment already deleted\"}}")));

        ModerationResult result = adapter.deleteComment(ctx, new ModerationTarget("wall", "10", "99", null, "-100"));

        assertThat(result.alreadyGone()).isTrue();
        assertThat(result.remoteId()).isEqualTo("99");
    }

    @Test
    void deleteCommentAccessDenied15IsNotAlreadyGone() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/wall.deleteComment"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"error\":{\"error_code\":15,\"error_msg\":\"Access denied\"}}")));

        assertThatThrownBy(() -> adapter.deleteComment(ctx, new ModerationTarget("wall", "10", "99", null, "-100")))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.PERMISSION));
    }

    @Test
    void executeBatchUsesVkExecute() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/execute"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response\":[1,1]}")));

        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        List<ModerationBatchItemResult> results = adapter.executeBatch(
                ctx,
                List.of(
                        new ModerationBatchItem(
                                "delete_post",
                                a1,
                                new ModerationTarget(null, "1", null, null, "-100"),
                                ModerationPayload.empty(),
                                "k1"),
                        new ModerationBatchItem(
                                "delete_comment",
                                a2,
                                new ModerationTarget("wall", "1", "2", null, "-100"),
                                ModerationPayload.empty(),
                                "k2")));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(1).success()).isTrue();
        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/execute"))
                .withRequestBody(containing("API.wall.delete"))
                .withRequestBody(containing("API.wall.deleteComment")));
    }

    @Test
    void moderationCapabilitiesAllTrue() {
        assertThat(adapter.moderationCapabilities().features().get(ModerationFeatureFlags.PUBLISH_SUGGESTED))
                .isTrue();
        assertThat(adapter.moderationCapabilities().features().get(ModerationFeatureFlags.RESTORE_COMMENT))
                .isTrue();
        assertThat(adapter.moderationCapabilities().features().get(ModerationFeatureFlags.BAN_USER))
                .isTrue();
    }
}
