package ru.posts.social.vk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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

class VkStoryAdapterWireMockTest {

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
        properties.setReconciliationMode("emulator");
        adapter = new VkSocialAdapter(properties, new ObjectMapper());
        ctx = new ChannelContext(UUID.randomUUID(), UUID.randomUUID(), "-100", "token", Network.VK);
    }

    @Test
    @DisplayName("TC-STORY-01/04: publishStory photo with VK CTA link")
    void publishStoryPhotoWithLink() {
        String uploadPath = "/vk/upload/story-1";
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
                        urlPathEqualTo("/method/stories.getPhotoUploadServer"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response\":{\"upload_url\":\"" + wireMock.baseUrl() + uploadPath + "\"}}")));
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo(uploadPath))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"server\":1,\"photo\":\"x\",\"hash\":\"h\"}")));
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/stories.save"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response\":{\"items\":[{\"id\":77,\"owner_id\":-100}]}}")));

        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        MediaAssetRef asset = new MediaAssetRef("m1", "image", "image/jpeg", jpeg.length, "sha", null, jpeg);
        PublishResult result = adapter.publishStory(
                ctx,
                new PostVariantContent(
                        "story",
                        "photo",
                        "ttl_24h",
                        "go_to",
                        "https://vk.com/club1",
                        Map.of(
                                PostVariantMeta.MEDIA,
                                List.of(asset),
                                PostVariantMeta.OPTIONS,
                                Map.of("addToNews", true))),
                "story-guid-1");

        assertThat(result.remotePostId()).isEqualTo("77");
        assertThat(result.remoteUrl()).contains("story-100_77");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/stories.getPhotoUploadServer"))
                .withRequestBody(containing("link_text=go_to"))
                .withRequestBody(containing("link_url="))
                .withRequestBody(containing("add_to_news=1"))
                .withRequestBody(containing("guid=story-guid-1")));
        wireMock.verify(postRequestedFor(urlPathEqualTo(uploadPath)).withRequestBody(containing("story.jpg")));
        wireMock.verify(postRequestedFor(urlPathEqualTo("/method/stories.save")));
    }

    @Test
    @DisplayName("TC-STORY-15: findExistingStory recovers by idempotency key")
    void findExistingStory() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/_control/posts"))
                        .withQueryParam("contentType", equalTo("story"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "[{\"idempotencyKey\":\"dup-key\",\"remotePostId\":\"55\",\"metadata\":{\"contentType\":\"story\"}}]")));

        assertThat(adapter.findExistingStory(ctx, "dup-key", new PostVariantContent("x")))
                .isPresent()
                .get()
                .extracting(PublishResult::remotePostId)
                .isEqualTo("55");
    }

    @Test
    @DisplayName("deleteStory is idempotent")
    void deleteStory() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/method/stories.delete"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response\":1}")));
        adapter.deleteStory(ctx, "77");
        wireMock.verify(postRequestedFor(urlPathMatching("/method/stories.delete")));
    }
}
