package ru.posts.api.ord;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

/**
 * Shared WireMock ORD stubs for QA-10 / TC-AD (VK ОРД + Яндекс ОРД).
 *
 * <p>Erid fixtures use the {@code test-erid-...} prefix to avoid gitleaks false positives.
 */
public final class OrdWireMockStubs {

    public static final String VK_ERID = "test-erid-vk-0001";
    public static final String YANDEX_ERID = "test-erid-yandex-0001";
    public static final String PREVIEW_ERID = "test-erid-preview-0001";

    private OrdWireMockStubs() {}

    public static void stubVkHappyPath(WireMockExtension wm) {
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/v1/person/my"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"name\":\"Publisher\"}")));
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(urlPathMatching("/v1/person/.*"))
                .willReturn(aResponse().withStatus(201)));
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(urlPathMatching("/v1/contract/.*"))
                .willReturn(aResponse().withStatus(201)));
        stubVkCreativeSuccess(wm, VK_ERID);
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(urlPathMatching("/v1/pad/.*"))
                .willReturn(aResponse().withStatus(201)));
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(urlPathMatching("/v1/invoice/.*"))
                .willReturn(aResponse().withStatus(201)));
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.delete(urlPathMatching("/v1/invoice/.*"))
                .willReturn(aResponse().withStatus(204)));
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/v1/statistics"))
                .willReturn(aResponse().withStatus(200)));
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathMatching("/v1/erid/.*"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"active\",\"erid\":\"" + VK_ERID + "\"}")));
    }

    public static void stubYandexHappyPath(WireMockExtension wm) {
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/api/v8/organization"))
                .willReturn(aResponse().withStatus(404).withBody("{\"detail\":\"not found\"}")));
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v8/organization"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v8/contract"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
        stubYandexCreativeSuccess(wm, YANDEX_ERID);
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v8/platforms"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v8/invoice"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v8/statistics"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/api/v8/creative"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"active\",\"token\":\"" + YANDEX_ERID + "\",\"id\":\"creative-1\"}")));
    }

    public static void stubVkCreativeSuccess(WireMockExtension wm, String erid) {
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(urlPathMatching("/v3/creative/.*"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"erid\":\"" + erid + "\",\"marker\":\"Реклама. erid: " + erid + "\"}")));
    }

    public static void stubYandexCreativeSuccess(WireMockExtension wm, String erid) {
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v8/creative"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\""
                                + erid
                                + "\",\"status\":\"Request accepted by ORD\",\"request_id\":\"req-test\"}")));
    }

    public static void stubVkCreativeHttpStatus(WireMockExtension wm, int status) {
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(urlPathMatching("/v3/creative/.*"))
                .willReturn(aResponse().withStatus(status).withBody("{\"error\":\"ord-error-" + status + "\"}")));
    }

    public static void stubYandexCreativeHttpStatus(WireMockExtension wm, int status) {
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v8/creative"))
                .willReturn(aResponse().withStatus(status).withBody("{\"detail\":\"ord-error-" + status + "\"}")));
    }

    public static void stubVkCreativeFixedDelay(WireMockExtension wm, int delayMs) {
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(urlPathMatching("/v3/creative/.*"))
                .willReturn(aResponse()
                        .withFixedDelay(delayMs)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"erid\":\"" + VK_ERID + "\"}")));
    }

    public static void stubYandexCreativeFixedDelay(WireMockExtension wm, int delayMs) {
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v8/creative"))
                .willReturn(aResponse()
                        .withFixedDelay(delayMs)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"" + YANDEX_ERID + "\",\"status\":\"Request accepted by ORD\"}")));
    }
}
