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
import java.util.Map;
import java.util.Optional;
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

/** BE-23 contract tests against OK API-shaped WireMock fixtures. */
class OkSocialAdapterBe23WireMockTest {

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
        properties.setReconciliationMode("api");
        properties.setApplicationKey("app-key");
        properties.setApplicationSecret("app-secret");
        adapter = new OkSocialAdapter(properties, new ObjectMapper());
        ctx = new ChannelContext(UUID.randomUUID(), UUID.randomUUID(), "gid-1", "token", Network.OK);
    }

    @Test
    void publishSendsJsonAttachmentAndSig() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/ok/fb.do"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"topic-9\",\"success\":true}")));

        PublishResult result = adapter.publish(ctx, new PostVariantContent("hello OK"), "idem-ok");

        assertThat(result.remotePostId()).isEqualTo("topic-9");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/ok/fb.do"))
                .withRequestBody(containing("method=mediatopic.post"))
                .withRequestBody(containing("application_key=app-key"))
                .withRequestBody(containing("sig="))
                .withRequestBody(containing("attachment=")));
    }

    @Test
    void findExistingViaStreamGet() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/ok/fb.do"))
                        .withRequestBody(containing("method=stream.get"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"media_topics\":[{\"id\":\"topic-9\",\"text\":\"hello OK\"}],\"entities\":{\"topics\":{\"topic-9\":{\"id\":\"topic-9\",\"text\":\"hello OK\"}}}}")));

        Optional<PublishResult> found = adapter.findExisting(ctx, "idem", new PostVariantContent("hello OK"));

        assertThat(found).isPresent();
        assertThat(found.get().remotePostId()).isEqualTo("topic-9");
        wireMock.verify(
                0,
                com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                        com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo("/_control/posts")));
    }

    @Test
    void authErrorCodeIsAuth() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/ok/fb.do"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error_code\":102,\"error_msg\":\"PARAM_SESSION_EXPIRED\"}")));

        assertThatThrownBy(() -> adapter.publish(ctx, new PostVariantContent("x"), "g"))
                .isInstanceOf(SocialApiException.class)
                .satisfies(
                        ex -> assertThat(((SocialApiException) ex).errorClass()).isEqualTo(ErrorClass.AUTH));
    }

    @Test
    void closeCommentsOptionMapped() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/ok/fb.do"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"t1\",\"success\":true}")));
        Map<String, Object> meta = Map.of(PostVariantMeta.OPTIONS, Map.of("closeComments", true));

        adapter.publish(ctx, new PostVariantContent("x", null, null, null, null, meta), "g");

        wireMock.verify(
                postRequestedFor(urlPathEqualTo("/ok/fb.do")).withRequestBody(containing("disable_comments=true")));
    }

    @Test
    void capabilitiesMarkPinUnsupported() {
        assertThat(adapter.capabilities().supportsUploadMedia()).isTrue();
        assertThat(adapter.capabilities().features().get("pin")).isFalse();
        assertThat(adapter.capabilities().features().get("firstComment")).isFalse();
    }
}
