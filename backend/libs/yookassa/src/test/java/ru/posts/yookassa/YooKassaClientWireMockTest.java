package ru.posts.yookassa;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class YooKassaClientWireMockTest {

    private static final String SHOP_ID = "test-shop-id-00000000";
    private static final String SECRET_KEY = "test-secret-key-not-a-real-credential";

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private YooKassaClient client;

    @BeforeEach
    void setUp() {
        YooKassaProperties properties = new YooKassaProperties();
        properties.setShopId(SHOP_ID);
        properties.setSecretKey(SECRET_KEY);
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setReturnUrl("http://localhost:3000/billing/return");
        properties.setReceiptEnabled(true);
        properties.setVatCode(1);
        client = new YooKassaClient(properties, new ObjectMapper());
    }

    @Test
    void createPaymentSendsAuthIdempotenceSaveMethodAndReceipt() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/payments"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {
                                  "id": "test-payment-id-0001",
                                  "status": "pending",
                                  "paid": false,
                                  "amount": {"value": "390.00", "currency": "RUB"},
                                  "confirmation": {
                                    "type": "redirect",
                                    "confirmation_url": "https://yookassa.test/confirm/0001"
                                  },
                                  "metadata": {"workspace_id": "ws-1"}
                                }
                                """)));

        Amount amount = new Amount("390.00", "RUB");
        Receipt receipt = new Receipt(
                new Receipt.Customer("owner@example.com"),
                List.of(new Receipt.ReceiptItem("Старт / месяц", "1.00", amount, 1)));
        CreatePaymentRequest request =
                new CreatePaymentRequest(amount, "Старт / месяц", Map.of("workspace_id", "ws-1"), receipt);

        PaymentResponse response = client.createPayment(request, "idem-key-create-0001");

        assertThat(response.id()).isEqualTo("test-payment-id-0001");
        assertThat(response.status()).isEqualTo("pending");
        assertThat(response.confirmation().confirmationUrl()).contains("confirm/0001");

        String expectedAuth = "Basic "
                + Base64.getEncoder().encodeToString((SHOP_ID + ":" + SECRET_KEY).getBytes(StandardCharsets.UTF_8));
        wireMock.verify(postRequestedFor(urlPathEqualTo("/payments"))
                .withHeader("Authorization", equalTo(expectedAuth))
                .withHeader("Idempotence-Key", equalTo("idem-key-create-0001"))
                .withRequestBody(containing("\"capture\":true"))
                .withRequestBody(containing("\"save_payment_method\":true"))
                .withRequestBody(containing("\"return_url\""))
                .withRequestBody(containing("\"receipt\""))
                .withRequestBody(containing("owner@example.com"))
                .withRequestBody(containing("workspace_id")));
    }

    @Test
    void getPaymentReturnsBody() {
        wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/payments/test-payment-id-0002"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {
                                  "id": "test-payment-id-0002",
                                  "status": "succeeded",
                                  "paid": true,
                                  "amount": {"value": "390.00", "currency": "RUB"},
                                  "payment_method": {
                                    "type": "bank_card",
                                    "id": "test-pm-0002",
                                    "saved": true
                                  }
                                }
                                """)));

        PaymentResponse response = client.getPayment("test-payment-id-0002");

        assertThat(response.id()).isEqualTo("test-payment-id-0002");
        assertThat(response.paid()).isTrue();
        assertThat(response.paymentMethod().id()).isEqualTo("test-pm-0002");
        assertThat(response.paymentMethod().saved()).isTrue();

        String expectedAuth = "Basic "
                + Base64.getEncoder().encodeToString((SHOP_ID + ":" + SECRET_KEY).getBytes(StandardCharsets.UTF_8));
        wireMock.verify(getRequestedFor(urlPathEqualTo("/payments/test-payment-id-0002"))
                .withHeader("Authorization", equalTo(expectedAuth)));
    }
}
