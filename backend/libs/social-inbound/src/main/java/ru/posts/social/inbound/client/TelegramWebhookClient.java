package ru.posts.social.inbound.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Telegram Bot API webhook helpers. */
public class TelegramWebhookClient {

    private final RestClient restClient;

    public TelegramWebhookClient(String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(trim(baseUrl)).build();
    }

    public JsonNode setWebhook(String botToken, String url, String secretToken, List<String> allowedUpdates) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", url);
        body.put("secret_token", secretToken);
        body.put("allowed_updates", allowedUpdates);
        body.put("drop_pending_updates", false);
        return restClient
                .post()
                .uri("/bot{token}/setWebhook", botToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode getWebhookInfo(String botToken) {
        return restClient
                .get()
                .uri("/bot{token}/getWebhookInfo", botToken)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode deleteWebhook(String botToken) {
        Map<String, Object> body = Map.of("drop_pending_updates", false);
        return restClient
                .post()
                .uri("/bot{token}/deleteWebhook", botToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private static String trim(String baseUrl) {
        String v = baseUrl == null || baseUrl.isBlank() ? "https://api.telegram.org" : baseUrl;
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
