package ru.posts.ord.vk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import ru.posts.ord.api.CreativeRegistrationResult;
import ru.posts.ord.api.OrdApiException;
import ru.posts.ord.api.OrdCreativeRequest;
import ru.posts.ord.api.OrdCredentials;
import ru.posts.ord.api.OrdErrorClass;
import ru.posts.ord.api.OrdProviderId;

class VkOrdProviderWireMockTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private VkOrdProvider provider;
    private OrdCredentials credentials;

    @BeforeEach
    void setUp() {
        OrdVkProperties properties = new OrdVkProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setReadTimeout(Duration.ofSeconds(5));
        provider = new VkOrdProvider(properties, new ObjectMapper());
        credentials = new OrdCredentials("test-api-key");
    }

    @Test
    void providerIdIsVkOrd() {
        assertThat(provider.providerId()).isEqualTo(OrdProviderId.VK_ORD);
    }

    @Test
    void registerCreativeReturnsErid() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(urlPathEqualTo("/v3/creative/creative-1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"erid\":\"2VtzqvwkhtU\",\"marker\":\"Реклама. erid: 2VtzqvwkhtU\"}")));

        CreativeRegistrationResult result = provider.registerCreative(credentials, sampleCreative("creative-1"));

        assertThat(result.externalId()).isEqualTo("creative-1");
        assertThat(result.erid()).isEqualTo("2VtzqvwkhtU");
        assertThat(result.providerRef()).contains("2VtzqvwkhtU");
        wireMock.verify(putRequestedFor(urlPathEqualTo("/v3/creative/creative-1"))
                .withHeader("Authorization", equalTo("Bearer test-api-key"))
                .withRequestBody(containing("contract_external_ids"))
                .withRequestBody(containing("contract-1")));
    }

    @Test
    void idempotentPutReturnsSameErid() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(urlPathEqualTo("/v3/creative/creative-1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"erid\":\"same-erid\"}")));

        CreativeRegistrationResult first = provider.registerCreative(credentials, sampleCreative("creative-1"));
        CreativeRegistrationResult second = provider.registerCreative(credentials, sampleCreative("creative-1"));

        assertThat(first.erid()).isEqualTo("same-erid");
        assertThat(second.erid()).isEqualTo("same-erid");
        wireMock.verify(2, putRequestedFor(urlPathEqualTo("/v3/creative/creative-1")));
    }

    @Test
    void http5xxIsRetryable() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(urlPathEqualTo("/v3/creative/creative-1"))
                .willReturn(aResponse().withStatus(503).withBody("{\"error\":\"unavailable\"}")));

        assertThatThrownBy(() -> provider.registerCreative(credentials, sampleCreative("creative-1")))
                .isInstanceOf(OrdApiException.class)
                .satisfies(ex -> {
                    OrdApiException ord = (OrdApiException) ex;
                    assertThat(ord.errorClass()).isEqualTo(OrdErrorClass.RETRYABLE);
                    assertThat(ord.httpStatus()).contains(503);
                    assertThat(provider.classifyError(ex)).isEqualTo(OrdErrorClass.RETRYABLE);
                });
    }

    @Test
    void http401IsAuth() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(urlPathEqualTo("/v3/creative/creative-1"))
                .willReturn(aResponse().withStatus(401).withBody("{\"error\":\"unauthorized\"}")));

        assertThatThrownBy(() -> provider.registerCreative(credentials, sampleCreative("creative-1")))
                .isInstanceOf(OrdApiException.class)
                .satisfies(ex -> {
                    OrdApiException ord = (OrdApiException) ex;
                    assertThat(ord.errorClass()).isEqualTo(OrdErrorClass.AUTH);
                    assertThat(provider.classifyError(ex)).isEqualTo(OrdErrorClass.AUTH);
                });
    }

    @Test
    void testConnectionCallsPersonMy() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/v1/person/my"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"name\":\"Me\"}")));

        provider.testConnection(credentials);

        wireMock.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathEqualTo("/v1/person/my"))
                .withHeader("Authorization", equalTo("Bearer test-api-key")));
    }

    private static OrdCreativeRequest sampleCreative(String externalId) {
        return new OrdCreativeRequest(
                externalId,
                List.of("contract-1"),
                "Ad name",
                "Description",
                List.of("30.15.1"),
                List.of("Hello"),
                "text_block",
                false,
                null,
                List.of(),
                null,
                null,
                "other",
                List.of());
    }
}
