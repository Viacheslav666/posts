package ru.posts.social.inbound.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Max Bot API subscription helpers (platform-api2.max.ru). */
public class MaxSubscriptionClient {

    private final RestClient restClient;

    public MaxSubscriptionClient(String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(trim(baseUrl, "https://platform-api2.max.ru"))
                .build();
    }

    public JsonNode subscribe(String botToken, String url, String secret, List<String> updateTypes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", url);
        body.put("secret", secret);
        if (updateTypes != null && !updateTypes.isEmpty()) {
            body.put("update_types", updateTypes);
        }
        return restClient
                .post()
                .uri("/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + botToken)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode listSubscriptions(String botToken) {
        return restClient
                .get()
                .uri("/subscriptions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + botToken)
                .retrieve()
                .body(JsonNode.class);
    }

    public void unsubscribe(String botToken, String url) {
        restClient
                .delete()
                .uri(uriBuilder ->
                        uriBuilder.path("/subscriptions").queryParam("url", url).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + botToken)
                .retrieve()
                .toBodilessEntity();
    }

    private static String trim(String baseUrl, String fallback) {
        String v = baseUrl == null || baseUrl.isBlank() ? fallback : baseUrl;
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
