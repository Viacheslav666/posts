package ru.posts.social.max;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
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

/** Max Bot API moderation actions (BE-33). */
public class MaxSocialModerationAdapter implements SocialModerationPort {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MaxSocialModerationAdapter(MaxAdapterProperties properties, ObjectMapper objectMapper) {
        String baseUrl = trimTrailingSlash(properties.baseUrl(), "https://platform-api2.max.ru");
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public MaxSocialModerationAdapter(MaxAdapterProperties properties) {
        this(properties, new ObjectMapper());
    }

    @Override
    public Network network() {
        return Network.MAX;
    }

    @Override
    public NetworkCapabilities moderationCapabilities() {
        return new NetworkCapabilities(Network.MAX, true, true, false, moderationFeatures());
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
        features.put(ModerationFeatureFlags.NOTIFY_AUTHOR, false);
        return features;
    }

    @Override
    public ModerationResult publishSuggested(ChannelContext ctx, ModerationTarget target, ModerationPayload payload) {
        throw new SocialApiException(ErrorClass.PERMANENT, "Max does not support publishSuggested", 400);
    }

    @Override
    public ModerationResult deletePost(ChannelContext ctx, ModerationTarget target) {
        String messageId = require(target == null ? null : target.remotePostId(), "remotePostId");
        try {
            restClient
                    .delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/max/messages")
                            .queryParam("message_id", messageId)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, auth(ctx))
                    .retrieve()
                    .toBodilessEntity();
            return new ModerationResult(messageId, Map.of("success", true));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return ModerationResult.alreadyGone(messageId);
            }
            // Fallback path style used by emulator / older clients.
            if (ex.getStatusCode().value() == 405 || ex.getStatusCode().value() == 400) {
                return deletePostByPath(ctx, messageId);
            }
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "Max deletePost failed: " + ex.getMessage(), null, null, ex);
        }
    }

    private ModerationResult deletePostByPath(ChannelContext ctx, String messageId) {
        try {
            restClient
                    .delete()
                    .uri("/max/messages/{messageId}", messageId)
                    .header(HttpHeaders.AUTHORIZATION, auth(ctx))
                    .retrieve()
                    .toBodilessEntity();
            return new ModerationResult(messageId, Map.of("success", true));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return ModerationResult.alreadyGone(messageId);
            }
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "Max deletePost failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public ModerationResult deleteComment(ChannelContext ctx, ModerationTarget target) {
        String messageId = require(
                target == null
                        ? null
                        : firstNonBlank(target.remotePostId(), extraString(target, "message_id", "messageId")),
                "remotePostId");
        String commentId = require(target == null ? null : target.remoteCommentId(), "remoteCommentId");
        try {
            JsonNode body = restClient
                    .delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/max/messages/{messageId}/comments")
                            .queryParam("comment_id", commentId)
                            .build(messageId))
                    .header(HttpHeaders.AUTHORIZATION, auth(ctx))
                    .retrieve()
                    .body(JsonNode.class);
            if (body != null && body.path("success").asBoolean(true) == false) {
                throw mapMaxBodyError(body);
            }
            return new ModerationResult(
                    commentId, body == null ? Map.of("success", true) : Map.of("response", toRaw(body)));
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return ModerationResult.alreadyGone(commentId);
            }
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "Max deleteComment failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public ModerationResult restoreComment(ChannelContext ctx, ModerationTarget target) {
        throw new SocialApiException(ErrorClass.PERMANENT, "Max does not support restoreComment", 400);
    }

    @Override
    public ModerationResult banUser(ChannelContext ctx, ModerationTarget target, ModerationPayload payload) {
        String chatId = firstNonBlank(
                target == null ? null : target.ownerId(), extraString(target, "chat_id", "chatId"), ctx.externalId());
        String userId = require(target == null ? null : target.remoteUserId(), "remoteUserId");
        try {
            restClient
                    .delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/max/chats/{chatId}/members")
                            .queryParam("user_id", userId)
                            .queryParam("block", "true")
                            .build(chatId))
                    .header(HttpHeaders.AUTHORIZATION, auth(ctx))
                    .retrieve()
                    .toBodilessEntity();
            return new ModerationResult(userId, Map.of("success", true));
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "Max banUser failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public ModerationResult unbanUser(ChannelContext ctx, ModerationTarget target) {
        throw new SocialApiException(ErrorClass.PERMANENT, "Max does not support unbanUser", 400);
    }

    @Override
    public ModerationResult replyToComment(ChannelContext ctx, ModerationTarget target, ModerationPayload payload) {
        ModerationPayload p = payload == null ? ModerationPayload.empty() : payload;
        String messageId = require(
                target == null
                        ? null
                        : firstNonBlank(target.remotePostId(), extraString(target, "message_id", "messageId")),
                "remotePostId");
        String text = firstNonBlank(p.messageText(), p.text());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text == null ? "" : text);
        String replyTo = firstNonBlank(p.replyToCommentId(), target == null ? null : target.remoteCommentId());
        if (replyTo != null) {
            body.put("reply_to", replyTo);
        }
        try {
            JsonNode response = restClient
                    .post()
                    .uri("/max/messages/{messageId}/comments", messageId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, auth(ctx))
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response != null && response.path("success").asBoolean(true) == false) {
                throw mapMaxBodyError(response);
            }
            String commentId = null;
            if (response != null) {
                commentId = response.path("comment").path("comment_id").asText(null);
                if (commentId == null) {
                    commentId = response.path("comment_id").asText(null);
                }
            }
            return new ModerationResult(
                    commentId, response == null ? Map.of("success", true) : Map.of("response", toRaw(response)));
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "Max replyToComment failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public ModerationResult notifyAuthor(ChannelContext ctx, ModerationTarget target, ModerationPayload payload) {
        throw new SocialApiException(ErrorClass.PERMANENT, "Max does not support notifyAuthor", 400);
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
            return classifyHttp(http);
        }
        if (error instanceof ResourceAccessException) {
            return ErrorClass.UNKNOWN_OUTCOME;
        }
        if (error instanceof RestClientException) {
            return ErrorClass.RETRYABLE;
        }
        return ErrorClass.RETRYABLE;
    }

    private SocialApiException mapHttpError(RestClientResponseException ex) {
        String raw = ex.getResponseBodyAsString();
        if (raw != null && !raw.isBlank()) {
            try {
                JsonNode body = objectMapper.readTree(raw);
                if (isAccessDenied(body, raw)) {
                    return new SocialApiException(
                            ErrorClass.PERMISSION,
                            body.path("message").asText(body.path("error").asText("access.denied")),
                            ex.getStatusCode().value(),
                            null,
                            ex);
                }
            } catch (SocialApiException social) {
                throw social;
            } catch (Exception ignored) {
                if (raw.toLowerCase(Locale.ROOT).contains("access.denied")) {
                    return new SocialApiException(
                            ErrorClass.PERMISSION,
                            "access.denied",
                            ex.getStatusCode().value(),
                            null,
                            ex);
                }
            }
        }
        int status = ex.getStatusCode().value();
        if (status == 429) {
            return new SocialApiException(
                    ErrorClass.RATE_LIMITED, "Max rate limited", status, Duration.ofSeconds(5), ex);
        }
        if (status >= 500) {
            return new SocialApiException(ErrorClass.RETRYABLE, "Max 5xx: " + status, status, null, ex);
        }
        if (status == 401) {
            return new SocialApiException(ErrorClass.AUTH, "Max auth failed", status, null, ex);
        }
        if (status == 403) {
            return new SocialApiException(ErrorClass.PERMISSION, "Max permission denied", status, null, ex);
        }
        return new SocialApiException(ErrorClass.PERMANENT, "Max error: " + status, status, null, ex);
    }

    private SocialApiException mapMaxBodyError(JsonNode body) {
        if (isAccessDenied(body, body.toString())) {
            return new SocialApiException(
                    ErrorClass.PERMISSION,
                    body.path("message").asText(body.path("error").asText("access.denied")));
        }
        return new SocialApiException(
                ErrorClass.PERMANENT,
                body.path("message").asText(body.path("error").asText("Max error")));
    }

    private static boolean isAccessDenied(JsonNode body, String raw) {
        if (raw != null && raw.toLowerCase(Locale.ROOT).contains("access.denied")) {
            return true;
        }
        if (body == null) {
            return false;
        }
        String code = body.path("code")
                .asText(body.path("error").asText(body.path("error_code").asText("")));
        String message = body.path("message").asText("");
        return "access.denied".equalsIgnoreCase(code)
                || message.toLowerCase(Locale.ROOT).contains("access.denied");
    }

    private ErrorClass classifyHttp(RestClientResponseException http) {
        String raw = http.getResponseBodyAsString();
        if (raw != null && raw.toLowerCase(Locale.ROOT).contains("access.denied")) {
            return ErrorClass.PERMISSION;
        }
        int status = http.getStatusCode().value();
        if (status == 429) {
            return ErrorClass.RATE_LIMITED;
        }
        if (status == 401) {
            return ErrorClass.AUTH;
        }
        if (status == 403) {
            return ErrorClass.PERMISSION;
        }
        if (status >= 500) {
            return ErrorClass.RETRYABLE;
        }
        return ErrorClass.PERMANENT;
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

    private static String auth(ChannelContext ctx) {
        return "Bearer " + token(ctx);
    }

    private static String token(ChannelContext ctx) {
        return ctx.accessToken() == null || ctx.accessToken().isBlank() ? "emulator-token" : ctx.accessToken();
    }

    private static String extraString(ModerationTarget target, String... keys) {
        if (target == null) {
            return null;
        }
        for (String key : keys) {
            Object v = target.extra().get(key);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v);
            }
        }
        return null;
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

    private static String trimTrailingSlash(String url, String fallback) {
        if (url == null || url.isBlank()) {
            return fallback;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
