package ru.posts.social.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
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
import ru.posts.social.api.PostVariantMeta;
import ru.posts.social.api.PublishResult;
import ru.posts.social.api.RemoteMediaRef;
import ru.posts.social.api.SocialAdapter;
import ru.posts.social.api.SocialApiException;

/**
 * Telegram Bot API adapter. Supports real multipart media ({@code sendPhoto}/{@code sendVideo}/
 * {@code sendDocument}/{@code sendMediaGroup}), forum topics ({@code message_thread_id}), inline
 * buttons and {@code disable_notification}. Sends {@code idempotency_key} for emulator dedupe;
 * {@link #findExisting} uses the emulator reconciliation helper.
 */
public class TelegramSocialAdapter implements SocialAdapter {

    private static final int MAX_ALBUM = 10;

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
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("sendMessage", true);
        features.put("sendPhoto", true);
        features.put("sendVideo", true);
        features.put("sendDocument", true);
        features.put("sendMediaGroup", true);
        features.put("deleteMessage", true);
        features.put("pinChatMessage", true);
        features.put("inlineMediaPublish", true);
        features.put("inlineKeyboard", true);
        features.put("disableNotification", true);
        features.put("messageThreadId", true);
        features.putAll(TelegramSocialModerationAdapter.moderationFeatures());
        return new NetworkCapabilities(Network.TG, true, true, false, features);
    }

    @Override
    public PublishResult publish(ChannelContext ctx, PostVariantContent content, String idempotencyKey) {
        String token = resolveToken(ctx);
        String chatId = ctx.externalId();
        String text = content == null || content.text() == null ? "" : content.text();
        Map<String, Object> meta = content == null ? Map.of() : content.meta();
        Map<String, Object> options = PostVariantMeta.options(meta);
        List<String> cachedFileIds = PostVariantMeta.attachments(meta);
        List<MediaAssetRef> pending = PostVariantMeta.media(meta);

        boolean disableNotification = Boolean.TRUE.equals(asBoolean(options.get("mute")));
        Object replyMarkup = buildReplyMarkup(options.get("buttons"));
        String threadId = resolveThreadId(ctx, options);

        try {
            if (pending.isEmpty() && cachedFileIds.isEmpty()) {
                return sendText(token, chatId, text, idempotencyKey, disableNotification, replyMarkup, threadId);
            }

            List<MediaItem> items = new ArrayList<>();
            for (String fileId : cachedFileIds) {
                items.add(MediaItem.fileId(fileId, guessKindFromFileId(fileId)));
            }
            for (MediaAssetRef asset : pending) {
                items.add(MediaItem.bytes(asset));
            }
            if (items.size() > MAX_ALBUM) {
                throw new SocialApiException(
                        ErrorClass.PERMANENT, "Telegram allows at most " + MAX_ALBUM + " media per album");
            }

            if (items.size() == 1) {
                return sendSingleMedia(
                        token,
                        chatId,
                        text,
                        idempotencyKey,
                        disableNotification,
                        replyMarkup,
                        threadId,
                        items.getFirst());
            }
            return sendMediaGroup(token, chatId, text, idempotencyKey, disableNotification, threadId, items);
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
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

    /**
     * Uploads a single asset via multipart {@code sendPhoto}/{@code sendVideo}/{@code sendDocument}
     * and returns a Telegram {@code file_id} when present (otherwise the message id). Preferred path
     * for albums is {@link #publish} with {@code meta.media} ({@code inlineMediaPublish}).
     */
    @Override
    public RemoteMediaRef uploadMedia(ChannelContext ctx, MediaAssetRef asset) {
        if (asset == null) {
            throw new SocialApiException(ErrorClass.PERMANENT, "Telegram uploadMedia requires asset");
        }
        String token = resolveToken(ctx);
        String chatId = ctx.externalId();
        String kind = normalizeKind(asset.kind(), asset.mime());
        String method =
                switch (kind) {
                    case "video" -> "sendVideo";
                    case "doc" -> "sendDocument";
                    default -> "sendPhoto";
                };
        String field =
                switch (kind) {
                    case "video" -> "video";
                    case "doc" -> "document";
                    default -> "photo";
                };
        try {
            MultipartBodyBuilder multipart = new MultipartBodyBuilder();
            multipart.part("chat_id", chatId);
            multipart.part("caption", "");
            multipart.part("idempotency_key", "media:" + (asset.id() == null ? "stub" : asset.id()));
            if (ctx.messageThreadId() != null && !ctx.messageThreadId().isBlank()) {
                multipart.part("message_thread_id", ctx.messageThreadId());
            }
            byte[] bytes = asset.hasContentBytes()
                    ? asset.contentBytes()
                    : new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
            multipart.part(field, new NamedByteArrayResource(bytes, assetFileName(asset, kind)));

            JsonNode body = restClient
                    .post()
                    .uri("/bot{token}/" + method, token)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart.build())
                    .retrieve()
                    .body(JsonNode.class);
            PublishResult published = parsePublishResult(body, chatId);
            String fileId = extractFileId(body, kind);
            return new RemoteMediaRef(fileId == null ? published.remotePostId() : fileId, kind);
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", ctx.externalId());
        body.put("text", text == null ? "" : text);
        body.put("reply_to_message_id", remotePostId);
        body.put("idempotency_key", "comment:" + remotePostId + ":" + (text == null ? "" : text.hashCode()));
        if (ctx.messageThreadId() != null && !ctx.messageThreadId().isBlank()) {
            body.put("message_thread_id", ctx.messageThreadId());
        }
        try {
            restClient
                    .post()
                    .uri("/bot{token}/sendMessage", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
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

    private PublishResult sendText(
            String token,
            String chatId,
            String text,
            String idempotencyKey,
            boolean disableNotification,
            Object replyMarkup,
            String threadId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "HTML");
        body.put("idempotency_key", idempotencyKey);
        if (disableNotification) {
            body.put("disable_notification", true);
        }
        if (replyMarkup != null) {
            body.put("reply_markup", replyMarkup);
        }
        if (threadId != null) {
            body.put("message_thread_id", threadId);
        }
        JsonNode response = restClient
                .post()
                .uri("/bot{token}/sendMessage", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return parsePublishResult(response, chatId);
    }

    private PublishResult sendSingleMedia(
            String token,
            String chatId,
            String caption,
            String idempotencyKey,
            boolean disableNotification,
            Object replyMarkup,
            String threadId,
            MediaItem item) {
        String kind = item.kind();
        String method =
                switch (kind) {
                    case "video" -> "sendVideo";
                    case "doc" -> "sendDocument";
                    default -> "sendPhoto";
                };
        String field =
                switch (kind) {
                    case "video" -> "video";
                    case "doc" -> "document";
                    default -> "photo";
                };

        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("chat_id", chatId);
        if (caption != null && !caption.isBlank()) {
            multipart.part("caption", caption);
            multipart.part("parse_mode", "HTML");
        }
        multipart.part("idempotency_key", idempotencyKey);
        if (disableNotification) {
            multipart.part("disable_notification", "true");
        }
        if (threadId != null) {
            multipart.part("message_thread_id", threadId);
        }
        if (replyMarkup != null) {
            try {
                multipart.part("reply_markup", objectMapper.writeValueAsString(replyMarkup));
            } catch (Exception ex) {
                throw new SocialApiException(ErrorClass.PERMANENT, "Failed to serialize reply_markup", null, null, ex);
            }
        }
        if (item.fileId() != null) {
            multipart.part(field, item.fileId());
        } else {
            multipart.part(field, new NamedByteArrayResource(item.bytes(), item.fileName()));
        }

        JsonNode body = restClient
                .post()
                .uri("/bot{token}/" + method, token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart.build())
                .retrieve()
                .body(JsonNode.class);
        PublishResult base = parsePublishResult(body, chatId);
        Map<String, RemoteMediaRef> uploaded = new LinkedHashMap<>();
        if (item.assetId() != null) {
            String fileId = extractFileId(body, kind);
            uploaded.put(item.assetId(), new RemoteMediaRef(fileId == null ? base.remotePostId() : fileId, kind));
        }
        return new PublishResult(base.remotePostId(), base.remoteUrl(), uploaded);
    }

    private PublishResult sendMediaGroup(
            String token,
            String chatId,
            String caption,
            String idempotencyKey,
            boolean disableNotification,
            String threadId,
            List<MediaItem> items) {
        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("chat_id", chatId);
        multipart.part("idempotency_key", idempotencyKey);
        if (disableNotification) {
            multipart.part("disable_notification", "true");
        }
        if (threadId != null) {
            multipart.part("message_thread_id", threadId);
        }

        List<Map<String, Object>> mediaJson = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            MediaItem item = items.get(i);
            String type =
                    switch (item.kind()) {
                        case "video" -> "video";
                        case "doc" -> "document";
                        default -> "photo";
                    };
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", type);
            if (item.fileId() != null) {
                entry.put("media", item.fileId());
            } else {
                String attachName = "file" + i;
                entry.put("media", "attach://" + attachName);
                multipart.part(attachName, new NamedByteArrayResource(item.bytes(), item.fileName()));
            }
            if (i == 0 && caption != null && !caption.isBlank()) {
                entry.put("caption", caption);
                entry.put("parse_mode", "HTML");
            }
            mediaJson.add(entry);
        }
        try {
            multipart.part("media", objectMapper.writeValueAsString(mediaJson));
        } catch (Exception ex) {
            throw new SocialApiException(ErrorClass.PERMANENT, "Failed to serialize media group", null, null, ex);
        }

        JsonNode body = restClient
                .post()
                .uri("/bot{token}/sendMediaGroup", token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart.build())
                .retrieve()
                .body(JsonNode.class);

        if (body == null || !body.path("ok").asBoolean(false)) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "Telegram ok=false");
        }
        JsonNode result = body.get("result");
        if (result == null || !result.isArray() || result.isEmpty()) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "Telegram media group missing result");
        }
        String messageId = result.get(0).path("message_id").asText(null);
        if (messageId == null) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "Telegram missing message_id");
        }

        Map<String, RemoteMediaRef> uploaded = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            MediaItem item = items.get(i);
            if (item.assetId() == null) {
                continue;
            }
            JsonNode msg = i < result.size() ? result.get(i) : result.get(0);
            String fileId = extractFileIdFromMessage(msg, item.kind());
            uploaded.put(item.assetId(), new RemoteMediaRef(fileId == null ? messageId : fileId, item.kind()));
        }
        return new PublishResult(messageId, remoteUrl(chatId, messageId), uploaded);
    }

    private static String resolveThreadId(ChannelContext ctx, Map<String, Object> options) {
        Object fromOptions = options.get("messageThreadId");
        if (fromOptions == null) {
            fromOptions = options.get("message_thread_id");
        }
        if (fromOptions == null && options.get("extra") instanceof Map<?, ?> extra) {
            fromOptions = extra.get("messageThreadId");
            if (fromOptions == null) {
                fromOptions = extra.get("message_thread_id");
            }
        }
        if (fromOptions != null && !String.valueOf(fromOptions).isBlank()) {
            return String.valueOf(fromOptions);
        }
        if (ctx.messageThreadId() != null && !ctx.messageThreadId().isBlank()) {
            return ctx.messageThreadId();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object buildReplyMarkup(Object buttonsRaw) {
        if (!(buttonsRaw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        List<Map<String, Object>> currentRow = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> button = new LinkedHashMap<>();
            Object text = map.get("text");
            if (text == null || String.valueOf(text).isBlank()) {
                continue;
            }
            button.put("text", String.valueOf(text));
            Object url = map.get("url");
            Object callback = map.get("callbackData");
            if (callback == null) {
                callback = map.get("callback_data");
            }
            if (url != null && !String.valueOf(url).isBlank()) {
                button.put("url", String.valueOf(url));
            } else if (callback != null) {
                button.put("callback_data", String.valueOf(callback));
            } else {
                continue;
            }
            currentRow.add(button);
            if (currentRow.size() >= 3) {
                rows.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }
        if (rows.isEmpty()) {
            return null;
        }
        return Map.of("inline_keyboard", rows);
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

    private static String extractFileId(JsonNode body, String kind) {
        if (body == null) {
            return null;
        }
        return extractFileIdFromMessage(body.get("result"), kind);
    }

    private static String extractFileIdFromMessage(JsonNode result, String kind) {
        if (result == null || result.isNull()) {
            return null;
        }
        if ("video".equals(kind) && result.has("video")) {
            return result.path("video").path("file_id").asText(null);
        }
        if ("doc".equals(kind) && result.has("document")) {
            return result.path("document").path("file_id").asText(null);
        }
        JsonNode photos = result.get("photo");
        if (photos != null && photos.isArray() && !photos.isEmpty()) {
            return photos.get(photos.size() - 1).path("file_id").asText(null);
        }
        if (result.has("document")) {
            return result.path("document").path("file_id").asText(null);
        }
        if (result.has("video")) {
            return result.path("video").path("file_id").asText(null);
        }
        return null;
    }

    private static String normalizeKind(String kind, String mime) {
        String k = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (k.contains("video") || m.startsWith("video/")) {
            return "video";
        }
        if (k.contains("doc") || k.contains("file") || m.startsWith("application/") || m.equals("text/plain")) {
            return "doc";
        }
        if (k.contains("audio") || m.startsWith("audio/")) {
            return "doc";
        }
        return "photo";
    }

    private static String guessKindFromFileId(String fileId) {
        if (fileId == null) {
            return "photo";
        }
        String lower = fileId.toLowerCase(Locale.ROOT);
        if (lower.startsWith("video") || lower.contains(":video:")) {
            return "video";
        }
        if (lower.startsWith("doc") || lower.contains(":doc:")) {
            return "doc";
        }
        return "photo";
    }

    private static String assetFileName(MediaAssetRef asset, String kind) {
        String id = asset.id() == null ? "file" : asset.id();
        return switch (kind) {
            case "video" -> id + ".mp4";
            case "doc" -> id + ".bin";
            default -> id + ".jpg";
        };
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

    @SuppressWarnings("ArrayRecordComponent")
    private record MediaItem(String assetId, String kind, String fileId, byte[] bytes, String fileName) {
        static MediaItem fileId(String fileId, String kind) {
            return new MediaItem(null, kind, fileId, null, null);
        }

        static MediaItem bytes(MediaAssetRef asset) {
            String kind = normalizeKind(asset.kind(), asset.mime());
            byte[] content = asset.hasContentBytes()
                    ? asset.contentBytes()
                    : new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
            return new MediaItem(asset.id(), kind, null, content, assetFileName(asset, kind));
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
