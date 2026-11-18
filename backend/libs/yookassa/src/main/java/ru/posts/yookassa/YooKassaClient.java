package ru.posts.yookassa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin HTTP client for YooKassa Payments API v3.
 *
 * @see <a href="https://yookassa.ru/developers/api">YooKassa API</a>
 */
public class YooKassaClient {

    private static final String IDEMPOTENCE_KEY_HEADER = "Idempotence-Key";

    private final RestClient restClient;
    private final YooKassaProperties properties;
    private final ObjectMapper objectMapper;

    public YooKassaClient(YooKassaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        String baseUrl = trimTrailingSlash(properties.baseUrl(), "https://api.yookassa.ru/v3");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> {
                    headers.setBasicAuth(properties.shopId(), properties.secretKey());
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .build();
    }

    public PaymentResponse createPayment(CreatePaymentRequest request, String idempotenceKey) {
        if (idempotenceKey == null || idempotenceKey.isBlank()) {
            throw new YooKassaException("Idempotence-Key is required");
        }
        String returnUrl = request.returnUrl() != null && !request.returnUrl().isBlank()
                ? request.returnUrl()
                : properties.returnUrl();
        ObjectNode body = objectMapper.createObjectNode();
        body.set("amount", objectMapper.valueToTree(request.amount()));
        body.put("capture", true);
        body.set("confirmation", objectMapper.valueToTree(Confirmation.redirect(returnUrl)));
        if (request.description() != null && !request.description().isBlank()) {
            body.put("description", request.description());
        }
        body.put("save_payment_method", true);
        Map<String, String> metadata = request.metadata();
        if (metadata != null && !metadata.isEmpty()) {
            body.set("metadata", objectMapper.valueToTree(metadata));
        }
        if (properties.receiptEnabled() && request.receipt() != null) {
            body.set("receipt", objectMapper.valueToTree(request.receipt()));
        }
        return postPayment(body, idempotenceKey);
    }

    /**
     * Charges a previously saved payment method ({@code payment_method_id}) without redirect
     * confirmation.
     */
    public PaymentResponse createRecurringPayment(
            Amount amount,
            String paymentMethodId,
            String description,
            Map<String, String> metadata,
            Receipt receipt,
            String idempotenceKey) {
        if (idempotenceKey == null || idempotenceKey.isBlank()) {
            throw new YooKassaException("Idempotence-Key is required");
        }
        if (paymentMethodId == null || paymentMethodId.isBlank()) {
            throw new YooKassaException("payment_method_id is required for recurring payment");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.set("amount", objectMapper.valueToTree(amount));
        body.put("capture", true);
        body.put("payment_method_id", paymentMethodId);
        if (description != null && !description.isBlank()) {
            body.put("description", description);
        }
        if (metadata != null && !metadata.isEmpty()) {
            body.set("metadata", objectMapper.valueToTree(metadata));
        }
        if (properties.receiptEnabled() && receipt != null) {
            body.set("receipt", objectMapper.valueToTree(receipt));
        }
        return postPayment(body, idempotenceKey);
    }

    public PaymentResponse getPayment(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new YooKassaException("paymentId is required");
        }
        try {
            PaymentResponse response = restClient
                    .get()
                    .uri("/payments/{paymentId}", paymentId)
                    .retrieve()
                    .body(PaymentResponse.class);
            if (response == null) {
                throw new YooKassaException("Empty YooKassa getPayment response");
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            throw new YooKassaException("YooKassa timeout: " + ex.getMessage(), ex);
        } catch (RestClientException ex) {
            throw new YooKassaException("YooKassa transport error: " + ex.getMessage(), ex);
        }
    }

    private PaymentResponse postPayment(ObjectNode body, String idempotenceKey) {
        try {
            PaymentResponse response = restClient
                    .post()
                    .uri("/payments")
                    .header(IDEMPOTENCE_KEY_HEADER, idempotenceKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .body(PaymentResponse.class);
            if (response == null) {
                throw new YooKassaException("Empty YooKassa createPayment response");
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            throw new YooKassaException("YooKassa timeout: " + ex.getMessage(), ex);
        } catch (RestClientException ex) {
            throw new YooKassaException("YooKassa transport error: " + ex.getMessage(), ex);
        }
    }

    private static YooKassaException mapHttpError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        return new YooKassaException("YooKassa HTTP " + status + ": " + ex.getResponseBodyAsString(), status, ex);
    }

    private static String trimTrailingSlash(String url, String fallback) {
        if (url == null || url.isBlank()) {
            return fallback;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
