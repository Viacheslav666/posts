package ru.posts.social.ok;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.MediaAssetRef;
import ru.posts.social.api.Network;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.PublishResult;
import ru.posts.social.api.RemoteMediaRef;

/**
 * QA-13: OK adapter contract tests against fixtures shaped like official OK API docs.
 * Reconciliation mode is {@code api} — {@code /_control} must never be called.
 */
class OkSocialAdapterQa13ContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        adapter = new OkSocialAdapter(properties, MAPPER);
        ctx = new ChannelContext(UUID.randomUUID(), UUID.randomUUID(), "gid-1", "token", Network.OK);
    }

    @Test
    @DisplayName("mediatopic.post success fixture → remotePostId")
    void mediatopicPostFromOfficialFixture() throws Exception {
        stubFbDo(fixture("mediatopic-post-success.json"));

        PublishResult result = adapter.publish(ctx, new PostVariantContent("hello OK"), "idem-ok-qa13");

        assertThat(result.remotePostId()).isEqualTo("topic-9");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/ok/fb.do"))
                .withRequestBody(containing("method=mediatopic.post"))
                .withRequestBody(containing("sig=")));
        assertNoControlCalls();
    }

    @Test
    @DisplayName("stream.get fixture reconciles existing topic without /_control")
    void streamGetFixtureReconciliation() throws Exception {
        stubFbDo(fixture("stream-get-topics.json"));

        Optional<PublishResult> found = adapter.findExisting(ctx, "idem", new PostVariantContent("hello OK"));

        assertThat(found).isPresent();
        assertThat(found.get().remotePostId()).isEqualTo("topic-9");
        assertNoControlCalls();
    }

    @Test
    @DisplayName("photosV2.getUploadUrl fixture drives uploadMedia")
    void photoUploadFromOfficialFixture() throws Exception {
        JsonNode uploadServer = MAPPER.readTree(fixture("photos-v2-getUploadUrl.json"));
        String uploadPath = "/ok/upload";
        ((ObjectNode) uploadServer).put("upload_url", wireMock.baseUrl() + uploadPath);
        stubFbDo(MAPPER.writeValueAsString(uploadServer));
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo(uploadPath))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"photos\":{\"photo\":{\"token\":\"tok-ok-1\"}}}")));

        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        MediaAssetRef asset = new MediaAssetRef("m1", "image", "image/jpeg", jpeg.length, "sha", null, jpeg);
        RemoteMediaRef remote = adapter.uploadMedia(ctx, asset);

        assertThat(remote.attachmentId()).isEqualTo("tok-ok-1");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/ok/fb.do"))
                .withRequestBody(containing("method=photosV2.getUploadUrl")));
        assertNoControlCalls();
    }

    private void assertNoControlCalls() {
        wireMock.verify(
                0,
                com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                        com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching("/_control/.*")));
        wireMock.verify(
                0,
                com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                        com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching("/_control/.*")));
    }

    private void stubFbDo(String body) {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/ok/fb.do"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private static String fixture(String name) throws Exception {
        String path = "fixtures/ok-api/" + name;
        try (InputStream in =
                OkSocialAdapterQa13ContractTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
