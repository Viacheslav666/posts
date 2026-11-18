package ru.posts.social.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.ErrorClass;
import ru.posts.social.api.MediaAssetRef;
import ru.posts.social.api.Network;
import ru.posts.social.api.NetworkCapabilities;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.PublishResult;
import ru.posts.social.api.RemoteMediaRef;
import ru.posts.social.api.SocialAdapter;
import ru.posts.social.api.SocialApiException;

/**
 * Telegram Bot API adapter (prototype) targeting the social emulator. Sends {@code
 * idempotency_key} so the emulator can dedupe; {@link #findExisting} uses the emulator reconciliation
 * helper.
 */
public class TelegramSocialAdapter implements SocialAdapter {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public TelegramSocialAdapter(TelegramAdapterProperties properties, ObjectMapper objectMapper) {
        this.baseUrl = trimTrailingSlash(properties.baseUrl());
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
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
    public NetworkCapabilities capabilities() {
        return new NetworkCapabilities(
                Network.TG,
                true,
                true,
                false,
                Map.of("sendMessage", true, "sendPhoto", true, "deleteMessage", true, "pinChatMessage", true));
    }

    @Override
    public PublishResult publish(ChannelContext ctx, PostVariantContent content, String idempotencyKey) {
        String token = resolveToken(ctx);
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
        } catch (ResourceAccessException ex) {
            // Emulator DROP / timeout: response may already exist — resolve via findExisting.
            throw new SocialApiException(
                    ErrorClass.UNKNOWN_OUTCOME, "Telegram call interrupted: " + ex.getMessage(), null, null, ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "Telegram transport error: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public Optional<PublishResult> findExisting(ChannelContext ctx, String idempotencyKey, PostVariantContent content) {
        return ru.posts.social.telegram.emulator.TelegramEmulatorReconciliation.find(restClient, ctx, idempotencyKey);
    }

    @Override
    public RemoteMediaRef uploadMedia(ChannelContext ctx, MediaAssetRef asset) {
        String token = resolveToken(ctx);
        String chatId = ctx.externalId();
        String photoRef = asset == null || asset.id() == null ? "stub-photo" : asset.id();
        try {
            JsonNode body = restClient
                    .post()
                    .uri("/bot{token}/sendPhoto", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "chat_id",
                            chatId,
                            "photo",
                            photoRef,
                            "caption",
                            "",
                            "idempotency_key",
                            "media:" + photoRef))
                    .retrieve()
                    .body(JsonNode.class);
            PublishResult published = parsePublishResult(body, chatId);
            String kind = asset == null || asset.kind() == null ? "photo" : asset.kind();
            return new RemoteMediaRef(published.remotePostId(), kind);
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "Telegram upload interrupted: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public void delete(ChannelContext ctx, String remotePostId) {
        String token = resolveToken(ctx);
        try {
            restClient
                    .post()
                    .uri("/bot{token}/deleteMessage", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", ctx.externalId(), "message_id", remotePostId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 400 || status == 404) {
                return;
            }
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "Telegram delete failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public void pin(ChannelContext ctx, String remotePostId, boolean pinned) {
        String token = resolveToken(ctx);
        String method = pinned ? "pinChatMessage" : "unpinChatMessage";
        try {
            restClient
                    .post()
                    .uri("/bot{token}/" + method, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", ctx.externalId(), "message_id", remotePostId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "Telegram pin failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public void comment(ChannelContext ctx, String remotePostId, String text) {
        String token = resolveToken(ctx);
        try {
            restClient
                    .post()
                    .uri("/bot{token}/sendMessage", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "chat_id",
                            ctx.externalId(),
                            "text",
                            text == null ? "" : text,
                            "reply_to_message_id",
                            remotePostId,
                            "idempotency_key",
                            "comment:" + remotePostId + ":" + (text == null ? "" : text.hashCode())))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "Telegram comment failed: " + ex.getMessage(), null, null, ex);
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
        if (error instanceof ResourceAccessException) {
            return ErrorClass.UNKNOWN_OUTCOME;
        }
        if (error instanceof RestClientException) {
            return ErrorClass.RETRYABLE;
        }
        return ErrorClass.RETRYABLE;
    }

    private static String resolveToken(ChannelContext ctx) {
        return ctx.accessToken() == null || ctx.accessToken().isBlank() ? "emulator-token" : ctx.accessToken();
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

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8090";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
