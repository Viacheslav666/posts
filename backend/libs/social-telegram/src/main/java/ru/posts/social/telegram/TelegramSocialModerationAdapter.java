package ru.posts.social.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import ru.posts.social.api.ModerationFeatureFlags;
import ru.posts.social.api.ModerationPayload;
import ru.posts.social.api.ModerationResult;
import ru.posts.social.api.ModerationTarget;
import ru.posts.social.api.Network;
import ru.posts.social.api.NetworkCapabilities;
import ru.posts.social.api.SocialApiException;
import ru.posts.social.api.SocialModerationPort;

/** Telegram Bot API moderation actions (BE-33). */
public class TelegramSocialModerationAdapter implements SocialModerationPort {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public TelegramSocialModerationAdapter(TelegramAdapterProperties properties, ObjectMapper objectMapper) {
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
    public NetworkCapabilities moderationCapabilities() {
        return new NetworkCapabilities(Network.TG, true, true, false, moderationFeatures());
    }

    static Map<String, Boolean> moderationFeatures() {
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put(ModerationFeatureFlags.PUBLISH_SUGGESTED, false);
        features.put(ModerationFeatureFlags.DELETE_POST, true);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_WALL, false);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_PHOTO, false);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_VIDEO, false);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_BOARD, false);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_MARKET, false);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_DISCUSSION, false);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_CHANNEL_COMMENT, true);
        features.put(ModerationFeatureFlags.RESTORE_COMMENT, false);
        features.put(ModerationFeatureFlags.BAN_USER, true);
        features.put(ModerationFeatureFlags.REPLY_TO_COMMENT, true);
        features.put(ModerationFeatureFlags.NOTIFY_AUTHOR, true);
        return features;
    }

    @Override
    public ModerationResult publishSuggested(ChannelContext ctx, ModerationTarget target, ModerationPayload payload) {
        throw new SocialApiException(ErrorClass.PERMANENT, "Telegram does not support publishSuggested", 400);
    }

    @Override
    public ModerationResult deletePost(ChannelContext ctx, ModerationTarget target) {
        String messageId = require(
                target == null ? null : firstNonBlank(target.remotePostId(), target.remoteCommentId()), "remotePostId");
        return deleteMessage(ctx, messageId);
    }

    @Override
    public ModerationResult deleteComment(ChannelContext ctx, ModerationTarget target) {
        String messageId = require(
                target == null ? null : firstNonBlank(target.remoteCommentId(), target.remotePostId()),
                "remoteCommentId");
        return deleteMessage(ctx, messageId);
    }

    private ModerationResult deleteMessage(ChannelContext ctx, String messageId) {
        String token = resolveToken(ctx);
        Map<String, Object> body = Map.of("chat_id", ctx.externalId(), "message_id", parseMessageId(messageId));
        try {
            JsonNode response = restClient
                    .post()
                    .uri("/bot{token}/deleteMessage", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            assertOk(response);
            return new ModerationResult(messageId, Map.of("ok", true));
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapDeleteError(ex);
        } catch (ResourceAccessException ex) {
            throw new SocialApiException(
                    ErrorClass.UNKNOWN_OUTCOME, "Telegram delete interrupted: " + ex.getMessage(), null, null, ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "Telegram deleteMessage failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public ModerationResult restoreComment(ChannelContext ctx, ModerationTarget target) {
        throw new SocialApiException(ErrorClass.PERMANENT, "Telegram does not support restoreComment", 400);
    }

    @Override
    public ModerationResult banUser(ChannelContext ctx, ModerationTarget target, ModerationPayload payload) {
        ModerationPayload p = payload == null ? ModerationPayload.empty() : payload;
        String token = resolveToken(ctx);
        String userId = require(target == null ? null : target.remoteUserId(), "remoteUserId");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", ctx.externalId());
        body.put("user_id", parseMessageId(userId));
        if (p.banEndDate() != null) {
            body.put("until_date", p.banEndDate().getEpochSecond());
        }
        Boolean restrictOnly = asBoolean(p.extra().get("restrictOnly"));
        String method = Boolean.TRUE.equals(restrictOnly) ? "restrictChatMember" : "banChatMember";
        if (Boolean.TRUE.equals(restrictOnly)) {
            body.put(
                    "permissions",
                    Map.of(
                            "can_send_messages",
                            false,
                            "can_send_media_messages",
                            false,
                            "can_send_other_messages",
                            false,
                            "can_add_web_page_previews",
                            false));
        }
        try {
            JsonNode response = restClient
                    .post()
                    .uri("/bot{token}/" + method, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            assertOk(response);
            return new ModerationResult(userId, Map.of("ok", true));
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "Telegram " + method + " failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public ModerationResult unbanUser(ChannelContext ctx, ModerationTarget target) {
        String token = resolveToken(ctx);
        String userId = require(target == null ? null : target.remoteUserId(), "remoteUserId");
        Map<String, Object> body = Map.of("chat_id", ctx.externalId(), "user_id", parseMessageId(userId));
        try {
            JsonNode response = restClient
                    .post()
                    .uri("/bot{token}/unbanChatMember", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            assertOk(response);
            return new ModerationResult(userId, Map.of("ok", true));
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "Telegram unbanChatMember failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public ModerationResult replyToComment(ChannelContext ctx, ModerationTarget target, ModerationPayload payload) {
        ModerationPayload p = payload == null ? ModerationPayload.empty() : payload;
        String token = resolveToken(ctx);
        String replyTo = firstNonBlank(
                p.replyToCommentId(),
                target == null ? null : firstNonBlank(target.remoteCommentId(), target.remotePostId()));
        String text = firstNonBlank(p.messageText(), p.text());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", ctx.externalId());
        body.put("text", text == null ? "" : text);
        if (replyTo != null) {
            body.put("reply_to_message_id", parseMessageId(replyTo));
            body.put("reply_parameters", Map.of("message_id", parseMessageId(replyTo)));
        }
        try {
            JsonNode response = restClient
                    .post()
                    .uri("/bot{token}/sendMessage", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            assertOk(response);
            String messageId = response.path("result").path("message_id").asText(null);
            return new ModerationResult(messageId, Map.of("result", toRaw(response.path("result"))));
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "Telegram reply failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public ModerationResult notifyAuthor(ChannelContext ctx, ModerationTarget target, ModerationPayload payload) {
        ModerationPayload p = payload == null ? ModerationPayload.empty() : payload;
        String token = resolveToken(ctx);
        String userId = require(target == null ? null : target.remoteUserId(), "remoteUserId");
        String text = firstNonBlank(p.messageText(), p.text());
        Map<String, Object> body = Map.of("chat_id", parseMessageId(userId), "text", text == null ? "" : text);
        try {
            JsonNode response = restClient
                    .post()
                    .uri("/bot{token}/sendMessage", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            assertOk(response);
            String messageId = response.path("result").path("message_id").asText(null);
            return new ModerationResult(messageId, Map.of("result", toRaw(response.path("result"))));
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "Telegram notifyAuthor failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public Optional<ModerationResult> reconcilePublishSuggested(
            ChannelContext ctx, ModerationTarget target, String idempotencyKey) {
        return Optional.empty();
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

    private void assertOk(JsonNode body) {
        if (body == null) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "Telegram empty response");
        }
        if (!body.path("ok").asBoolean(false)) {
            String description = body.path("description").asText("Telegram ok=false");
            throw classifyTelegramDescription(description, null);
        }
    }

    private SocialApiException mapDeleteError(RestClientResponseException ex) {
        String raw = ex.getResponseBodyAsString();
        String description = extractDescription(raw);
        if (description != null) {
            return classifyTelegramDescription(description, ex.getStatusCode().value());
        }
        return mapHttpError(ex);
    }

    private SocialApiException classifyTelegramDescription(String description, Integer status) {
        String lower = description == null ? "" : description.toLowerCase(Locale.ROOT);
        if (lower.contains("message can't be deleted")
                || lower.contains("message can_t be deleted")
                || lower.contains("can't be deleted for everyone")
                || lower.contains("48")
                || lower.contains("too old")) {
            ErrorClass clazz =
                    lower.contains("48") || lower.contains("too old") ? ErrorClass.PERMANENT : ErrorClass.PERMISSION;
            return new SocialApiException(clazz, description, status);
        }
        if (lower.contains("not found") || lower.contains("message to delete not found")) {
            return new SocialApiException(ErrorClass.PERMANENT, description, status);
        }
        if (lower.contains("forbidden") || lower.contains("not enough rights") || lower.contains("have no rights")) {
            return new SocialApiException(ErrorClass.PERMISSION, description, status);
        }
        return new SocialApiException(
                ErrorClass.PERMANENT, description == null ? "Telegram ok=false" : description, status);
    }

    private String extractDescription(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode body = objectMapper.readTree(raw);
            return body.path("description").asText(null);
        } catch (Exception ignored) {
            return raw;
        }
    }

    private SocialApiException mapHttpError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        String description = extractDescription(ex.getResponseBodyAsString());
        if (description != null) {
            SocialApiException classified = classifyTelegramDescription(description, status);
            if (classified.errorClass() == ErrorClass.PERMISSION || classified.errorClass() == ErrorClass.PERMANENT) {
                return classified;
            }
        }
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
        return new SocialApiException(
                ErrorClass.PERMANENT,
                description == null ? "Telegram error: " + status : description,
                status,
                null,
                ex);
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

    private Object toRaw(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(node, Object.class);
        } catch (IllegalArgumentException ex) {
            return node.toString();
        }
    }

    private static Object parseMessageId(String messageId) {
        try {
            return Long.parseLong(messageId);
        } catch (NumberFormatException ex) {
            return messageId;
        }
    }

    private static String resolveToken(ChannelContext ctx) {
        return ctx.accessToken() == null || ctx.accessToken().isBlank() ? "emulator-token" : ctx.accessToken();
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new SocialApiException(ErrorClass.PERMANENT, "missing " + name);
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8090";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
