package ru.posts.social.vk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.ErrorClass;
import ru.posts.social.api.ModerationBatchItem;
import ru.posts.social.api.ModerationBatchItemResult;
import ru.posts.social.api.ModerationFeatureFlags;
import ru.posts.social.api.ModerationPayload;
import ru.posts.social.api.ModerationResult;
import ru.posts.social.api.ModerationTarget;
import ru.posts.social.api.Network;
import ru.posts.social.api.NetworkCapabilities;
import ru.posts.social.api.SocialApiException;
import ru.posts.social.api.SocialModerationPort;

/**
 * VK remote moderation / suggest actions (BE-33). Uses form-urlencoded {@code /method/{method}}
 * like {@link VkSocialAdapter}; {@link #executeBatch} prefers {@code execute} when {@code items}
 * size &gt; 1.
 */
public class VkSocialModerationAdapter implements SocialModerationPort {

    private static final int EXECUTE_MAX = 25;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiVersion;

    public VkSocialModerationAdapter(VkAdapterProperties properties, ObjectMapper objectMapper) {
        String baseUrl = trimTrailingSlash(properties.baseUrl(), "https://api.vk.com");
        this.apiVersion =
                properties.apiVersion() == null || properties.apiVersion().isBlank()
                        ? "5.199"
                        : properties.apiVersion();
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
        return Network.VK;
    }

    @Override
    public NetworkCapabilities moderationCapabilities() {
        return new NetworkCapabilities(Network.VK, true, true, true, allModerationFeatures(true));
    }

    /** Feature flags shared with {@link VkSocialAdapter#capabilities()}. */
    static Map<String, Boolean> allModerationFeatures(boolean enabled) {
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put(ModerationFeatureFlags.PUBLISH_SUGGESTED, enabled);
        features.put(ModerationFeatureFlags.DELETE_POST, enabled);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_WALL, enabled);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_PHOTO, enabled);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_VIDEO, enabled);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_BOARD, enabled);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_MARKET, enabled);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_DISCUSSION, enabled);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_CHANNEL_COMMENT, enabled);
        features.put(ModerationFeatureFlags.RESTORE_COMMENT, enabled);
        features.put(ModerationFeatureFlags.BAN_USER, enabled);
        features.put(ModerationFeatureFlags.REPLY_TO_COMMENT, enabled);
        features.put(ModerationFeatureFlags.NOTIFY_AUTHOR, enabled);
        return features;
    }

    @Override
    public ModerationResult publishSuggested(ChannelContext ctx, ModerationTarget target, ModerationPayload payload) {
        ModerationPayload p = payload == null ? ModerationPayload.empty() : payload;
        MultiValueMap<String, String> form = baseForm(ctx);
        form.add("owner_id", ownerId(ctx, target));
        form.add("post_id", require(target == null ? null : target.remotePostId(), "remotePostId"));
        form.add("from_group", "1");
        if (Boolean.TRUE.equals(p.signed())) {
            form.add("signed", "1");
        }
        String message = firstNonBlank(p.text(), p.messageText());
        if (message != null && !message.isBlank()) {
            form.add("message", message);
        }
        String attachments = firstExtraString(target, p, "attachments");
        if (attachments != null && !attachments.isBlank()) {
            form.add("attachments", attachments);
        }
        String guid = firstExtraString(target, p, "guid", "idempotencyKey", "idempotency_key");
        if (guid != null) {
            form.add("guid", guid);
        }
        JsonNode body = call("wall.post", form);
        JsonNode response = body.path("response");
        String postId = response.path("post_id").asText(null);
        if (postId == null) {
            postId = response.isNumber() ? response.asText() : null;
        }
        if (postId == null) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "VK wall.post missing post_id");
        }
        return new ModerationResult(postId, Map.of("response", toRaw(response)));
    }

    @Override
    public ModerationResult deletePost(ChannelContext ctx, ModerationTarget target) {
        MultiValueMap<String, String> form = baseForm(ctx);
        form.add("owner_id", ownerId(ctx, target));
        form.add("post_id", require(target == null ? null : target.remotePostId(), "remotePostId"));
        JsonNode body = callAllowingAlreadyGone("wall.delete", form);
        if (body == null) {
            return ModerationResult.alreadyGone(target.remotePostId());
        }
        return new ModerationResult(target.remotePostId(), Map.of("response", toRaw(body.path("response"))));
    }

    @Override
    public ModerationResult deleteComment(ChannelContext ctx, ModerationTarget target) {
        return commentAction(ctx, target, false);
    }

    @Override
    public ModerationResult restoreComment(ChannelContext ctx, ModerationTarget target) {
        return commentAction(ctx, target, true);
    }

    private ModerationResult commentAction(ChannelContext ctx, ModerationTarget target, boolean restore) {
        String commentId = require(target == null ? null : target.remoteCommentId(), "remoteCommentId");
        String type = normalizeCommentType(target == null ? null : target.commentType());
        String method =
                switch (type) {
                    case "photo" -> restore ? "photos.restoreComment" : "photos.deleteComment";
                    case "video" -> restore ? "video.restoreComment" : "video.deleteComment";
                    case "market" -> restore ? "market.restoreComment" : "market.deleteComment";
                    case "board", "discussion" -> restore ? "board.restoreComment" : "board.deleteComment";
                    default -> restore ? "wall.restoreComment" : "wall.deleteComment";
                };
        MultiValueMap<String, String> form = baseForm(ctx);
        if ("board".equals(type) || "discussion".equals(type)) {
            form.add("group_id", groupId(ctx, target));
            form.add("topic_id", requireExtra(target, "topic_id", "topicId"));
            form.add("comment_id", commentId);
        } else {
            form.add("owner_id", ownerId(ctx, target));
            form.add("comment_id", commentId);
        }
        if (restore) {
            JsonNode body = call(method, form);
            return new ModerationResult(commentId, Map.of("response", toRaw(body.path("response"))));
        }
        JsonNode body = callAllowingAlreadyGone(method, form);
        if (body == null) {
            return ModerationResult.alreadyGone(commentId);
        }
        return new ModerationResult(commentId, Map.of("response", toRaw(body.path("response"))));
    }

    @Override
    public ModerationResult banUser(ChannelContext ctx, ModerationTarget target, ModerationPayload payload) {
        ModerationPayload p = payload == null ? ModerationPayload.empty() : payload;
        MultiValueMap<String, String> form = baseForm(ctx);
        form.add("group_id", groupId(ctx, target));
        form.add("owner_id", require(target == null ? null : target.remoteUserId(), "remoteUserId"));
        if (p.banEndDate() != null) {
            form.add("end_date", String.valueOf(p.banEndDate().getEpochSecond()));
        }
        if (p.banReason() != null) {
            form.add("reason", String.valueOf(p.banReason()));
        }
        if (p.banComment() != null) {
            form.add("comment", p.banComment());
        }
        if (p.banCommentVisible() != null) {
            form.add("comment_visible", Boolean.TRUE.equals(p.banCommentVisible()) ? "1" : "0");
        }
        JsonNode body = call("groups.ban", form);
        return new ModerationResult(target.remoteUserId(), Map.of("response", toRaw(body.path("response"))));
    }

    @Override
    public ModerationResult unbanUser(ChannelContext ctx, ModerationTarget target) {
        MultiValueMap<String, String> form = baseForm(ctx);
        form.add("group_id", groupId(ctx, target));
        form.add("owner_id", require(target == null ? null : target.remoteUserId(), "remoteUserId"));
        JsonNode body = call("groups.unban", form);
        return new ModerationResult(target.remoteUserId(), Map.of("response", toRaw(body.path("response"))));
    }

    @Override
    public ModerationResult replyToComment(ChannelContext ctx, ModerationTarget target, ModerationPayload payload) {
        ModerationPayload p = payload == null ? ModerationPayload.empty() : payload;
        String message = firstNonBlank(p.messageText(), p.text());
        MultiValueMap<String, String> form = baseForm(ctx);
        form.add("owner_id", ownerId(ctx, target));
        form.add("post_id", require(target == null ? null : target.remotePostId(), "remotePostId"));
        form.add("message", message == null ? "" : message);
        form.add("from_group", stripMinus(ownerId(ctx, target)));
        String replyTo = firstNonBlank(p.replyToCommentId(), target == null ? null : target.remoteCommentId());
        if (replyTo != null) {
            form.add("reply_to_comment", replyTo);
        }
        JsonNode body = call("wall.createComment", form);
        String commentId = body.path("response").path("comment_id").asText(null);
        if (commentId == null) {
            commentId = body.path("response").asText(null);
        }
        return new ModerationResult(commentId, Map.of("response", toRaw(body.path("response"))));
    }

    @Override
    public ModerationResult notifyAuthor(ChannelContext ctx, ModerationTarget target, ModerationPayload payload) {
        ModerationPayload p = payload == null ? ModerationPayload.empty() : payload;
        String userId = require(target == null ? null : target.remoteUserId(), "remoteUserId");
        String groupId = groupId(ctx, target);
        MultiValueMap<String, String> allowedForm = baseForm(ctx);
        allowedForm.add("group_id", groupId);
        allowedForm.add("user_id", userId);
        JsonNode allowedBody = call("messages.isMessagesFromGroupAllowed", allowedForm);
        boolean allowed = allowedBody.path("response").path("is_allowed").asInt(0) == 1
                || allowedBody.path("response").asBoolean(false)
                || allowedBody.path("response").asInt(0) == 1;
        if (!allowed) {
            throw new SocialApiException(
                    ErrorClass.PERMISSION, "VK messages from group not allowed for user " + userId);
        }
        String message = firstNonBlank(p.messageText(), p.text());
        MultiValueMap<String, String> sendForm = baseForm(ctx);
        sendForm.add("random_id", String.valueOf(ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE)));
        sendForm.add("message", message == null ? "" : message);
        sendForm.add("peer_id", userId);
        sendForm.add("user_id", userId);
        JsonNode body = call("messages.send", sendForm);
        String messageId = body.path("response").asText(null);
        return new ModerationResult(messageId, Map.of("response", toRaw(body.path("response"))));
    }

    @Override
    public Optional<ModerationResult> reconcilePublishSuggested(
            ChannelContext ctx, ModerationTarget target, String idempotencyKey) {
        String postId = target == null ? null : target.remotePostId();
        if (postId == null || postId.isBlank()) {
            return Optional.empty();
        }
        String owner = ownerId(ctx, target);
        try {
            MultiValueMap<String, String> suggestsForm = baseForm(ctx);
            suggestsForm.add("owner_id", owner);
            suggestsForm.add("filter", "suggests");
            suggestsForm.add("count", "100");
            JsonNode suggests = call("wall.get", suggestsForm);
            JsonNode items = suggests.path("response").path("items");
            boolean stillSuggested = false;
            if (items.isArray()) {
                for (JsonNode item : items) {
                    if (postId.equals(item.path("id").asText(null))) {
                        stillSuggested = true;
                        break;
                    }
                }
            }
            MultiValueMap<String, String> byIdForm = baseForm(ctx);
            byIdForm.add("posts", owner + "_" + postId);
            JsonNode byId = call("wall.getById", byIdForm);
            JsonNode byIdItems = byId.path("response");
            if (byIdItems.isObject()) {
                byIdItems = byIdItems.path("items");
            }
            if (byIdItems.isArray() && !byIdItems.isEmpty()) {
                JsonNode published = byIdItems.get(0);
                String foundId = published.path("id").asText(postId);
                boolean isSuggest =
                        "suggest".equalsIgnoreCase(published.path("post_type").asText(""))
                                || published
                                        .path("post_type")
                                        .asText("")
                                        .toLowerCase(Locale.ROOT)
                                        .contains("suggest");
                if (!isSuggest || !stillSuggested) {
                    return Optional.of(new ModerationResult(foundId, Map.of("response", toRaw(published))));
                }
            }
            if (!stillSuggested) {
                return Optional.of(new ModerationResult(postId, Map.of("reconciled", "not_in_suggests")));
            }
            return Optional.empty();
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<ModerationBatchItemResult> executeBatch(ChannelContext ctx, List<ModerationBatchItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (items.size() <= 1) {
            return SocialModerationPort.super.executeBatch(ctx, items);
        }
        try {
            List<ModerationBatchItemResult> out = new ArrayList<>(items.size());
            for (int offset = 0; offset < items.size(); offset += EXECUTE_MAX) {
                List<ModerationBatchItem> chunk = items.subList(offset, Math.min(offset + EXECUTE_MAX, items.size()));
                out.addAll(executeChunk(ctx, chunk));
            }
            return List.copyOf(out);
        } catch (RuntimeException ex) {
            return SocialModerationPort.super.executeBatch(ctx, items);
        }
    }

    private List<ModerationBatchItemResult> executeChunk(ChannelContext ctx, List<ModerationBatchItem> chunk) {
        String code = buildExecuteCode(ctx, chunk);
        MultiValueMap<String, String> form = baseForm(ctx);
        form.add("code", code);
        JsonNode body = call("execute", form);
        JsonNode response = body.path("response");
        if (!response.isArray()) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "VK execute missing response array");
        }
        List<ModerationBatchItemResult> out = new ArrayList<>(chunk.size());
        for (int i = 0; i < chunk.size(); i++) {
            ModerationBatchItem item = chunk.get(i);
            JsonNode itemNode = i < response.size() ? response.get(i) : null;
            out.add(parseExecuteItem(item, itemNode));
        }
        return out;
    }

    private ModerationBatchItemResult parseExecuteItem(ModerationBatchItem item, JsonNode itemNode) {
        if (itemNode == null || itemNode.isNull()) {
            return ModerationBatchItemResult.fail(item.actionId(), ErrorClass.RETRYABLE, "VK execute null result");
        }
        if (itemNode.has("error_code") || itemNode.has("error_msg")) {
            int code = itemNode.path("error_code").asInt(0);
            String msg = itemNode.path("error_msg").asText("VK execute item error");
            if (isAlreadyGoneCode(code, msg)
                    && ("delete_post".equals(normalizeKind(item.kind()))
                            || "delete_comment".equals(normalizeKind(item.kind())))) {
                String remoteId = item.target() == null
                        ? null
                        : ("delete_post".equals(normalizeKind(item.kind()))
                                ? item.target().remotePostId()
                                : item.target().remoteCommentId());
                return ModerationBatchItemResult.ok(item.actionId(), ModerationResult.alreadyGone(remoteId));
            }
            return ModerationBatchItemResult.fail(item.actionId(), classifyModerationVkErrorCode(code), msg);
        }
        String remoteId = extractRemoteId(item, itemNode);
        return ModerationBatchItemResult.ok(
                item.actionId(), new ModerationResult(remoteId, Map.of("response", toRaw(itemNode))));
    }

    private String extractRemoteId(ModerationBatchItem item, JsonNode itemNode) {
        if (itemNode.isObject()) {
            String postId = itemNode.path("post_id").asText(null);
            if (postId != null) {
                return postId;
            }
            String commentId = itemNode.path("comment_id").asText(null);
            if (commentId != null) {
                return commentId;
            }
        }
        if (itemNode.isNumber() || itemNode.isTextual()) {
            String asText = itemNode.asText();
            if (!"1".equals(asText) && !"0".equals(asText)) {
                return asText;
            }
        }
        ModerationTarget target = item.target();
        if (target == null) {
            return asTextOrNull(itemNode);
        }
        return switch (normalizeKind(item.kind())) {
            case "publish_suggested", "delete_post" -> target.remotePostId();
            case "delete_comment", "restore_comment", "reply_comment" -> target.remoteCommentId();
            case "ban_user", "unban_user", "notify_author" -> target.remoteUserId();
            default -> asTextOrNull(itemNode);
        };
    }

    private String buildExecuteCode(ChannelContext ctx, List<ModerationBatchItem> chunk) {
        StringBuilder sb = new StringBuilder("return [");
        for (int i = 0; i < chunk.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(toApiCall(ctx, chunk.get(i)));
        }
        sb.append("];");
        return sb.toString();
    }

    private String toApiCall(ChannelContext ctx, ModerationBatchItem item) {
        ModerationPayload payload = item.payload() == null ? ModerationPayload.empty() : item.payload();
        ModerationTarget target = item.target();
        return switch (normalizeKind(item.kind())) {
            case "publish_suggested" -> {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("owner_id", ownerId(ctx, target));
                params.put("post_id", require(target.remotePostId(), "remotePostId"));
                params.put("from_group", "1");
                if (Boolean.TRUE.equals(payload.signed())) {
                    params.put("signed", "1");
                }
                String message = firstNonBlank(payload.text(), payload.messageText());
                if (message != null && !message.isBlank()) {
                    params.put("message", message);
                }
                String attachments = firstExtraString(target, payload, "attachments");
                if (attachments != null && !attachments.isBlank()) {
                    params.put("attachments", attachments);
                }
                String guid = firstNonBlank(
                        item.idempotencyKey(),
                        firstExtraString(target, payload, "guid", "idempotencyKey", "idempotency_key"));
                if (guid != null) {
                    params.put("guid", guid);
                }
                yield "API.wall.post(" + vkObject(params) + ")";
            }
            case "delete_post" -> {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("owner_id", ownerId(ctx, target));
                params.put("post_id", require(target.remotePostId(), "remotePostId"));
                yield "API.wall.delete(" + vkObject(params) + ")";
            }
            case "delete_comment", "restore_comment" -> {
                boolean restore = "restore_comment".equals(normalizeKind(item.kind()));
                String type = normalizeCommentType(target.commentType());
                Map<String, String> params = new LinkedHashMap<>();
                String method;
                if ("board".equals(type) || "discussion".equals(type)) {
                    method = restore ? "board.restoreComment" : "board.deleteComment";
                    params.put("group_id", groupId(ctx, target));
                    params.put("topic_id", requireExtra(target, "topic_id", "topicId"));
                    params.put("comment_id", require(target.remoteCommentId(), "remoteCommentId"));
                } else {
                    method = switch (type) {
                        case "photo" -> restore ? "photos.restoreComment" : "photos.deleteComment";
                        case "video" -> restore ? "video.restoreComment" : "video.deleteComment";
                        case "market" -> restore ? "market.restoreComment" : "market.deleteComment";
                        default -> restore ? "wall.restoreComment" : "wall.deleteComment";
                    };
                    params.put("owner_id", ownerId(ctx, target));
                    params.put("comment_id", require(target.remoteCommentId(), "remoteCommentId"));
                }
                yield "API." + method + "(" + vkObject(params) + ")";
            }
            case "ban_user" -> {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("group_id", groupId(ctx, target));
                params.put("owner_id", require(target.remoteUserId(), "remoteUserId"));
                if (payload.banEndDate() != null) {
                    params.put("end_date", String.valueOf(payload.banEndDate().getEpochSecond()));
                }
                if (payload.banReason() != null) {
                    params.put("reason", String.valueOf(payload.banReason()));
                }
                if (payload.banComment() != null) {
                    params.put("comment", payload.banComment());
                }
                if (payload.banCommentVisible() != null) {
                    params.put("comment_visible", Boolean.TRUE.equals(payload.banCommentVisible()) ? "1" : "0");
                }
                yield "API.groups.ban(" + vkObject(params) + ")";
            }
            case "unban_user" -> {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("group_id", groupId(ctx, target));
                params.put("owner_id", require(target.remoteUserId(), "remoteUserId"));
                yield "API.groups.unban(" + vkObject(params) + ")";
            }
            case "reply_comment" -> {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("owner_id", ownerId(ctx, target));
                params.put("post_id", require(target.remotePostId(), "remotePostId"));
                String message = firstNonBlank(payload.messageText(), payload.text());
                params.put("message", message == null ? "" : message);
                params.put("from_group", stripMinus(ownerId(ctx, target)));
                String replyTo = firstNonBlank(payload.replyToCommentId(), target.remoteCommentId());
                if (replyTo != null) {
                    params.put("reply_to_comment", replyTo);
                }
                yield "API.wall.createComment(" + vkObject(params) + ")";
            }
            case "notify_author" -> {
                Map<String, String> params = new LinkedHashMap<>();
                String message = firstNonBlank(payload.messageText(), payload.text());
                params.put("user_id", require(target.remoteUserId(), "remoteUserId"));
                params.put("peer_id", require(target.remoteUserId(), "remoteUserId"));
                params.put("message", message == null ? "" : message);
                params.put(
                        "random_id", String.valueOf(ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE)));
                yield "API.messages.send(" + vkObject(params) + ")";
            }
            default -> throw new SocialApiException(ErrorClass.PERMANENT, "unknown moderation kind: " + item.kind());
        };
    }

    private static String vkObject(Map<String, String> params) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(e.getKey()).append(':');
            String v = e.getValue() == null ? "" : e.getValue();
            if (v.matches("-?\\d+")) {
                sb.append(v);
            } else {
                sb.append('"')
                        .append(v.replace("\\", "\\\\").replace("\"", "\\\""))
                        .append('"');
            }
        }
        sb.append('}');
        return sb.toString();
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
            if (status == 401) {
                return ErrorClass.AUTH;
            }
            if (status == 403) {
                return ErrorClass.PERMISSION;
            }
            if (status >= 500) {
                return ErrorClass.RETRYABLE;
            }
            ErrorClass fromBody = classifyVkErrorBody(http.getResponseBodyAsString());
            return fromBody != null ? fromBody : ErrorClass.PERMANENT;
        }
        if (error instanceof ResourceAccessException) {
            return ErrorClass.UNKNOWN_OUTCOME;
        }
        if (error instanceof RestClientException) {
            return ErrorClass.RETRYABLE;
        }
        return ErrorClass.RETRYABLE;
    }

    private JsonNode call(String method, MultiValueMap<String, String> form) {
        return callInternal(method, form, false);
    }

    /** @return null when the remote object is already gone (idempotent delete success) */
    private JsonNode callAllowingAlreadyGone(String method, MultiValueMap<String, String> form) {
        return callInternal(method, form, true);
    }

    private JsonNode callInternal(String method, MultiValueMap<String, String> form, boolean allowAlreadyGone) {
        try {
            JsonNode body = restClient
                    .post()
                    .uri("/method/" + method)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (allowAlreadyGone && isAlreadyGoneBody(body)) {
                return null;
            }
            assertNoVkError(body);
            return body;
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            throw new SocialApiException(
                    ErrorClass.UNKNOWN_OUTCOME, "VK call interrupted: " + ex.getMessage(), null, null, ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "VK " + method + " failed: " + ex.getMessage(), null, null, ex);
        }
    }

    private static boolean isAlreadyGoneBody(JsonNode body) {
        if (body == null) {
            return false;
        }
        JsonNode error = body.get("error");
        if (error == null || error.isNull()) {
            return false;
        }
        int code = error.path("error_code").asInt(0);
        String msg = error.path("error_msg").asText("");
        return isAlreadyGoneCode(code, msg);
    }

    private MultiValueMap<String, String> baseForm(ChannelContext ctx) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("access_token", token(ctx));
        form.add("v", apiVersion);
        return form;
    }

    private void assertNoVkError(JsonNode body) {
        if (body == null) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "VK empty response");
        }
        JsonNode error = body.get("error");
        if (error == null || error.isNull()) {
            return;
        }
        int code = error.path("error_code").asInt(0);
        String msg = error.path("error_msg").asText("VK error " + code);
        ErrorClass clazz = classifyModerationVkErrorCode(code);
        Duration retryAfter = clazz == ErrorClass.RATE_LIMITED ? Duration.ofHours(1) : null;
        throw new SocialApiException(clazz, msg, null, retryAfter, null);
    }

    private ErrorClass classifyVkErrorBody(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode body = objectMapper.readTree(raw);
            JsonNode error = body.get("error");
            if (error != null && error.has("error_code")) {
                return classifyModerationVkErrorCode(error.path("error_code").asInt());
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    /** Moderation mapping: 15/27/203 → PERMISSION (not AUTH). */
    static ErrorClass classifyModerationVkErrorCode(int code) {
        return switch (code) {
            case 6, 9 -> ErrorClass.RATE_LIMITED;
            case 10, 1 -> ErrorClass.RETRYABLE;
            case 5 -> ErrorClass.AUTH;
            case 15, 27, 203 -> ErrorClass.PERMISSION;
            case 14 -> ErrorClass.PERMANENT;
            default -> ErrorClass.PERMANENT;
        };
    }

    /**
     * VK error 15 is "Access denied" and is also returned for missing wall/comment permissions.
     * Treat a delete as already-gone only when the message says the object is gone, or for the
     * dedicated not-found codes 104 and 242.
     */
    static boolean isAlreadyGoneCode(int code, String msg) {
        String lower = msg == null ? "" : msg.toLowerCase(Locale.ROOT);
        boolean textSaysGone = lower.contains("already")
                || lower.contains("has been deleted")
                || lower.contains("was deleted")
                || lower.contains("not found")
                || lower.contains("cannot be found")
                || lower.contains("was removed")
                || lower.contains("deleted");
        if (code == 15) {
            return textSaysGone;
        }
        if (code == 104 || code == 242) {
            return true;
        }
        return textSaysGone;
    }

    private SocialApiException mapHttpError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status == 429) {
            return new SocialApiException(
                    ErrorClass.RATE_LIMITED, "VK rate limited", status, Duration.ofSeconds(5), ex);
        }
        if (status >= 500) {
            return new SocialApiException(ErrorClass.RETRYABLE, "VK 5xx: " + status, status, null, ex);
        }
        if (status == 401) {
            return new SocialApiException(ErrorClass.AUTH, "VK auth failed", status, null, ex);
        }
        if (status == 403) {
            return new SocialApiException(ErrorClass.PERMISSION, "VK permission denied", status, null, ex);
        }
        ErrorClass fromBody = classifyVkErrorBody(ex.getResponseBodyAsString());
        if (fromBody != null) {
            return new SocialApiException(fromBody, "VK error", status, null, ex);
        }
        return new SocialApiException(ErrorClass.PERMANENT, "VK error: " + status, status, null, ex);
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

    private static String ownerId(ChannelContext ctx, ModerationTarget target) {
        if (target != null && target.ownerId() != null && !target.ownerId().isBlank()) {
            return target.ownerId();
        }
        if (target != null) {
            Object extra = target.extra().get("owner_id");
            if (extra == null) {
                extra = target.extra().get("ownerId");
            }
            if (extra != null && !String.valueOf(extra).isBlank()) {
                return String.valueOf(extra);
            }
        }
        return ctx.externalId();
    }

    private static String groupId(ChannelContext ctx, ModerationTarget target) {
        if (target != null) {
            Object extra = target.extra().get("group_id");
            if (extra == null) {
                extra = target.extra().get("groupId");
            }
            if (extra != null && !String.valueOf(extra).isBlank()) {
                return stripMinus(String.valueOf(extra));
            }
        }
        return stripMinus(ownerId(ctx, target));
    }

    private static String requireExtra(ModerationTarget target, String... keys) {
        if (target != null) {
            for (String key : keys) {
                Object v = target.extra().get(key);
                if (v != null && !String.valueOf(v).isBlank()) {
                    return String.valueOf(v);
                }
            }
        }
        throw new SocialApiException(ErrorClass.PERMANENT, "missing target.extra." + keys[0]);
    }

    private static String firstExtraString(ModerationTarget target, ModerationPayload payload, String... keys) {
        if (target != null) {
            for (String key : keys) {
                Object v = target.extra().get(key);
                if (v != null && !String.valueOf(v).isBlank()) {
                    return String.valueOf(v);
                }
            }
        }
        if (payload != null) {
            for (String key : keys) {
                Object v = payload.extra().get(key);
                if (v != null && !String.valueOf(v).isBlank()) {
                    return String.valueOf(v);
                }
            }
        }
        return null;
    }

    private static String normalizeCommentType(String commentType) {
        if (commentType == null || commentType.isBlank()) {
            return "wall";
        }
        return commentType.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeKind(String kind) {
        return kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
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

    private static String asTextOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText(null);
    }

    private static String token(ChannelContext ctx) {
        return ctx.accessToken() == null || ctx.accessToken().isBlank() ? "emulator-token" : ctx.accessToken();
    }

    private static String stripMinus(String ownerId) {
        if (ownerId == null) {
            return "0";
        }
        return ownerId.startsWith("-") ? ownerId.substring(1) : ownerId;
    }

    private static String trimTrailingSlash(String url, String fallback) {
        if (url == null || url.isBlank()) {
            return fallback;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
