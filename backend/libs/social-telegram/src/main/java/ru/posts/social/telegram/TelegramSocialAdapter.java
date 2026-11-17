package ru.posts.social.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.ErrorClass;
import ru.posts.social.api.Network;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.PublishResult;
import ru.posts.social.api.SocialAdapter;
import ru.posts.social.api.SocialApiException;

/**
 * Telegram Bot API adapter (prototype) targeting the social emulator. Sends {@code
 * idempotency_key} so the emulator can dedupe; {@link #findExisting} uses emulator control API.
 */
public class TelegramSocialAdapter implements SocialAdapter {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public TelegramSocialAdapter(TelegramAdapterProperties properties, ObjectMapper objectMapper) {
        this.baseUrl = trimTrailingSlash(properties.baseUrl());
        this.objectMapper = objectMapper;
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Network network() {
        return Network.TG;
    }

    @Override
    public PublishResult publish(ChannelContext ctx, PostVariantContent content, String idempotencyKey) {
        String token = ctx.accessToken() == null || ctx.accessToken().isBlank() ? "emulator-token" : ctx.accessToken();
        String chatId = ctx.externalId();
        String text = content == null || content.text() == null ? "" : content.text();
        try {
            JsonNode body = restClient
                    .post()
                    .uri("/bot{token}/sendMessage", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "chat_id", chatId,
                            "text", text,
                            "parse_mode", "HTML",
                            "idempotency_key", idempotencyKey))
                    .retrieve()
                    .body(JsonNode.class);
            return parsePublishResult(body, chatId);
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            // Emulator DROP returns aborted connection after creating the post.
            throw new SocialApiException(
                    ErrorClass.UNKNOWN_OUTCOME, "Telegram call interrupted: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public Optional<PublishResult> findExisting(ChannelContext ctx, String idempotencyKey, PostVariantContent content) {
        try {
            JsonNode posts = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/_control/posts")
                            .queryParam("network", "telegram")
                            .queryParam("channelId", ctx.externalId())
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (posts == null || !posts.isArray()) {
                return Optional.empty();
            }
            for (JsonNode post : posts) {
                if (idempotencyKey != null && idempotencyKey.equals(textOrNull(post, "idempotencyKey"))) {
                    String remoteId = textOrNull(post, "remotePostId");
                    if (remoteId != null) {
                        return Optional.of(new PublishResult(remoteId, remoteUrl(ctx.externalId(), remoteId)));
                    }
                }
            }
            return Optional.empty();
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    @Override
    public ErrorClass classifyError(Throwable error) {
        if (error instanceof SocialApiException social) {
            return social.errorClass();
        }
        if (error instanceof RestClientResponseException http) {
            int status = http.getStatusCode().value();
            if (status == 429) {
                return ErrorClass.RATE_LIMITED;
            }
            if (status >= 500) {
                return ErrorClass.RETRYABLE;
            }
            if (status == 401 || status == 403) {
                return ErrorClass.AUTH;
            }
            return ErrorClass.PERMANENT;
        }
        if (error instanceof RestClientException) {
            return ErrorClass.RETRYABLE;
        }
        return ErrorClass.RETRYABLE;
    }

    private SocialApiException mapHttpError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status == 429) {
            Duration retryAfter = parseRetryAfter(ex);
            return new SocialApiException(ErrorClass.RATE_LIMITED, "Telegram rate limited", status, retryAfter, ex);
        }
        if (status >= 500) {
            return new SocialApiException(ErrorClass.RETRYABLE, "Telegram 5xx: " + status, status, null, ex);
        }
        if (status == 401 || status == 403) {
            return new SocialApiException(ErrorClass.AUTH, "Telegram auth failed", status, null, ex);
        }
        // Emulator DROP may surface as 499
        if (status == 499) {
            return new SocialApiException(ErrorClass.UNKNOWN_OUTCOME, "Telegram response lost", status, null, ex);
        }
        return new SocialApiException(ErrorClass.PERMANENT, "Telegram error: " + status, status, null, ex);
    }

    private Duration parseRetryAfter(RestClientResponseException ex) {
        HttpHeaders headers = ex.getResponseHeaders();
        List<String> header = headers == null ? null : headers.get("Retry-After");
        if (header != null && !header.isEmpty()) {
            try {
                return Duration.ofSeconds(Long.parseLong(header.getFirst()));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            if (body.has("parameters") && body.get("parameters").has("retry_after")) {
                return Duration.ofSeconds(
                        body.get("parameters").get("retry_after").asLong());
            }
        } catch (Exception ignored) {
            // ignore
        }
        return Duration.ofSeconds(5);
    }

    private PublishResult parsePublishResult(JsonNode body, String chatId) {
        if (body == null || !body.path("ok").asBoolean(false)) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "Telegram ok=false");
        }
        JsonNode result = body.get("result");
        if (result == null) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "Telegram missing result");
        }
        String messageId = result.path("message_id").asText(null);
        if (messageId == null) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "Telegram missing message_id");
        }
        return new PublishResult(messageId, remoteUrl(chatId, messageId));
    }

    private static String remoteUrl(String chatId, String messageId) {
        return "https://t.me/c/" + chatId + "/" + messageId;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8090";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
