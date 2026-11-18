package ru.posts.ord.yandex;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
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

class YandexOrdProviderWireMockTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private YandexOrdProvider provider;
    private OrdCredentials credentials;

    @BeforeEach
    void setUp() {
        OrdYandexProperties properties = new OrdYandexProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setReadTimeout(Duration.ofSeconds(5));
        provider = new YandexOrdProvider(properties, new ObjectMapper());
        credentials = new OrdCredentials("yandex-token");
    }

    @Test
    void providerIdIsYandexOrd() {
        assertThat(provider.providerId()).isEqualTo(OrdProviderId.YANDEX_ORD);
    }

    @Test
    void registerCreativeReturnsTokenAsErid() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v8/creative"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {
                                  "request_id": "req-1",
                                  "unixtimestamp": 1,
                                  "object_type": "creative",
                                  "status": "Request accepted by ORD",
                                  "token": "test-erid-yandex-0001",
                                  "object_ids": ["creative-1"]
                                }
                                """)));

        CreativeRegistrationResult result = provider.registerCreative(credentials, sampleCreative("creative-1"));

        assertThat(result.externalId()).isEqualTo("creative-1");
        assertThat(result.erid()).isEqualTo("test-erid-yandex-0001");
        assertThat(result.providerRef()).isEqualTo("req-1");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/api/v8/creative"))
                .withHeader("Authorization", equalTo("Bearer yandex-token"))
                .withRequestBody(containing("\"form\":\"text-block\""))
                .withRequestBody(containing("kktuCodes"))
                .withRequestBody(containing("textData"))
                .withRequestBody(containing("contractIds")));
    }

    @Test
    void idempotentPostReturnsSameErid() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v8/creative"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {
                                  "request_id": "req-2",
                                  "unixtimestamp": 1,
                                  "object_type": "creative",
                                  "status": "Request accepted by ORD",
                                  "token": "same-token"
                                }
                                """)));

        CreativeRegistrationResult first = provider.registerCreative(credentials, sampleCreative("creative-1"));
        CreativeRegistrationResult second = provider.registerCreative(credentials, sampleCreative("creative-1"));

        assertThat(first.erid()).isEqualTo("same-token");
        assertThat(second.erid()).isEqualTo("same-token");
        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/api/v8/creative")));
    }

    @Test
    void http5xxIsRetryable() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v8/creative"))
                .willReturn(aResponse().withStatus(500).withBody("{\"detail\":\"boom\"}")));

        assertThatThrownBy(() -> provider.registerCreative(credentials, sampleCreative("creative-1")))
                .isInstanceOf(OrdApiException.class)
                .satisfies(ex -> {
                    OrdApiException ord = (OrdApiException) ex;
                    assertThat(ord.errorClass()).isEqualTo(OrdErrorClass.RETRYABLE);
                    assertThat(provider.classifyError(ex)).isEqualTo(OrdErrorClass.RETRYABLE);
                });
    }

    @Test
    void http401IsAuth() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v8/creative"))
                .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> provider.registerCreative(credentials, sampleCreative("creative-1")))
                .isInstanceOf(OrdApiException.class)
                .satisfies(ex -> assertThat(((OrdApiException) ex).errorClass()).isEqualTo(OrdErrorClass.AUTH));
    }

    @Test
    void testConnectionAccepts404AsReachable() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/api/v8/organization"))
                .willReturn(aResponse().withStatus(404)));

        provider.testConnection(credentials);
    }

    @Test
    void testConnectionRejects401() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/api/v8/organization"))
                .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> provider.testConnection(credentials))
                .isInstanceOf(OrdApiException.class)
                .satisfies(ex -> assertThat(((OrdApiException) ex).errorClass()).isEqualTo(OrdErrorClass.AUTH));
    }

    private static OrdCreativeRequest sampleCreative(String externalId) {
        return new OrdCreativeRequest(
                externalId,
                List.of("contract-1"),
                "Ad name",
                "Description",
                List.of("1.1.1"),
                List.of("Hello text"),
                "text-block",
                false,
                null,
                List.of("https://example.com"),
                null,
                null,
                null,
                List.of());
    }
}
