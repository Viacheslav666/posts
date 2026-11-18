package ru.posts.social.vk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
import ru.posts.social.api.PostVariantMeta;
import ru.posts.social.api.PublishResult;
import ru.posts.social.api.RemoteMediaRef;

/**
 * QA-13: VK adapter contract tests against fixtures recorded from official API 5.199 docs.
 * Reconciliation mode is {@code api} — {@code /_control} must never be called.
 */
class VkSocialAdapterQa13ContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        properties.setReadTimeout(Duration.ofSeconds(5));
        properties.setReconciliationMode("api");
        adapter = new VkSocialAdapter(properties, MAPPER);
        ctx = new ChannelContext(UUID.randomUUID(), UUID.randomUUID(), "-100", "token", Network.VK);
    }

    @Test
    @DisplayName("wall.post success fixture → remotePostId")
    void wallPostFromOfficialFixture() throws Exception {
        stubMethod("/method/wall.post", fixture("wall-post-success.json"));

        PublishResult result = adapter.publish(ctx, new PostVariantContent("hello from posts"), "guid-qa13");

        assertThat(result.remotePostId()).isEqualTo("42");
        wireMock.verify(
                postRequestedFor(urlPathEqualTo("/method/wall.post")).withRequestBody(containing("guid=guid-qa13")));
        assertNoControlCalls();
    }

    @Test
    @DisplayName("wall.get fixture reconciles existing post without /_control")
    void wallGetFixtureReconciliation() throws Exception {
        JsonNode wallGet = MAPPER.readTree(fixture("wall-get-items.json"));
        // Refresh date so findExisting window accepts the item.
        long now = System.currentTimeMillis() / 1000L;
        ((com.fasterxml.jackson.databind.node.ObjectNode)
                        wallGet.path("response").path("items").get(0))
                .put("date", now);
        stubMethod("/method/wall.get", MAPPER.writeValueAsString(wallGet));

        Optional<PublishResult> found = adapter.findExisting(ctx, "idem", new PostVariantContent("hello from posts"));

        assertThat(found).isPresent();
        assertThat(found.get().remotePostId()).isEqualTo("42");
        assertNoControlCalls();
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/method/wall.post")));
    }

    @Test
    @DisplayName("photos.saveWallPhoto fixture + upload server fixture")
    void photoUploadFromOfficialFixtures() throws Exception {
        JsonNode uploadServer = MAPPER.readTree(fixture("photos-get-wall-upload-server.json"));
        String uploadUrl = wireMock.baseUrl() + "/upload";
        ((com.fasterxml.jackson.databind.node.ObjectNode) uploadServer.path("response")).put("upload_url", uploadUrl);
        stubMethod("/method/photos.getWallUploadServer", MAPPER.writeValueAsString(uploadServer));
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/upload"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"server\":1,\"photo\":\"[x]\",\"hash\":\"h\"}")));
        stubMethod("/method/photos.saveWallPhoto", fixture("photos-save-wall-photo.json"));

        byte[] jpeg = new byte[] {
            (byte) 0xFF,
            (byte) 0xD8,
            (byte) 0xFF,
            (byte) 0xE0,
            0x00,
            0x10,
            0x4A,
            0x46,
            0x49,
            0x46,
            0x00,
            0x01,
            0x01,
            0x00,
            0x00,
            0x01,
            0x00,
            0x01,
            0x00,
            0x00,
            (byte) 0xFF,
            (byte) 0xD9
        };
        MediaAssetRef asset = new MediaAssetRef("a1", "image", "image/jpeg", jpeg.length, "sha", null, jpeg);
        RemoteMediaRef remote = adapter.uploadMedia(ctx, asset);

        assertThat(remote.attachmentId()).isEqualTo("photo-100_457239017");
        assertNoControlCalls();
    }

    @Test
    @DisplayName("polls.create + wall.pin fixtures")
    void pollAndPinFromOfficialFixtures() throws Exception {
        stubMethod("/method/polls.create", fixture("polls-create-success.json"));
        stubMethod("/method/wall.post", fixture("wall-post-success.json"));
        stubMethod("/method/wall.pin", fixture("wall-pin-success.json"));

        Map<String, Object> meta =
                Map.of(PostVariantMeta.OPTIONS, Map.of("poll", Map.of("question", "Q?", "answers", List.of("A", "B"))));
        PublishResult published =
                adapter.publish(ctx, new PostVariantContent("poll", null, null, null, null, meta), "guid-poll");
        assertThat(published.remotePostId()).isEqualTo("42");
        adapter.pin(ctx, "42", true);
        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/wall.pin")));
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

    private void stubMethod(String path, String body) {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo(path))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private static String fixture(String name) throws Exception {
        String path = "fixtures/vk-api-5.199/" + name;
        try (InputStream in =
                VkSocialAdapterQa13ContractTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
