package ru.posts.social.max;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
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
 * Max Bot API adapter ({@code POST /messages}, {@code POST /uploads}). Supports media attachments,
 * inline keyboard and {@code notify=false} for mute.
 */
public class MaxSocialAdapter implements SocialAdapter {

    private static final int MAX_ATTACHMENTS = 12;

    private final RestClient restClient;
    private final String baseUrl;

    public MaxSocialAdapter(MaxAdapterProperties properties) {
        this.baseUrl = trimTrailingSlash(properties.baseUrl(), "https://platform-api2.max.ru");
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
        return Network.MAX;
    }

    @Override
    public NetworkCapabilities capabilities() {
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("messages", true);
        features.put("uploads", true);
        features.put("bearerAuth", true);
        features.put("inlineKeyboard", true);
        features.put("disableNotification", true);
        features.putAll(MaxSocialModerationAdapter.moderationFeatures());
        return new NetworkCapabilities(Network.MAX, true, true, false, Map.copyOf(features));
    }

    @Override
    public PublishResult publish(ChannelContext ctx, PostVariantContent content, String idempotencyKey) {
        Map<String, Object> meta = content == null ? Map.of() : content.meta();
        Map<String, Object> options = PostVariantMeta.options(meta);
        List<String> attachmentTokens = new ArrayList<>(PostVariantMeta.attachments(meta));
        attachmentTokens.addAll(uploadPendingMedia(ctx, content));

        if (attachmentTokens.size() > MAX_ATTACHMENTS) {
            throw new SocialApiException(
                    ErrorClass.PERMANENT, "Max allows at most " + MAX_ATTACHMENTS + " attachments per message");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", ctx.externalId());
        payload.put("text", content == null || content.text() == null ? "" : content.text());
        payload.put("format", "html");
        payload.put("idempotency_key", idempotencyKey);
        if (Boolean.TRUE.equals(asBoolean(options.get("mute")))) {
            payload.put("notify", false);
        }

        List<Map<String, Object>> attachments = new ArrayList<>();
        for (String tokenRef : attachmentTokens) {
            attachments.add(Map.of(
                    "type", guessAttachmentType(tokenRef),
                    "payload", Map.of("token", stripTypePrefix(tokenRef))));
        }
        Object keyboard = buildInlineKeyboard(options.get("buttons"));
        if (keyboard != null) {
            attachments.add(Map.of("type", "inline_keyboard", "payload", keyboard));
        }
        if (!attachments.isEmpty()) {
            payload.put("attachments", attachments);
        }

        try {
            JsonNode body = restClient
                    .post()
                    .uri("/max/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(ctx))
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            return parsePublish(body, ctx.externalId());
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            throw new SocialApiException(
                    ErrorClass.UNKNOWN_OUTCOME, "Max call interrupted: " + ex.getMessage(), null, null, ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "Max transport error: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public Optional<PublishResult> findExisting(ChannelContext ctx, String idempotencyKey, PostVariantContent content) {
        return ru.posts.social.max.emulator.MaxEmulatorReconciliation.find(restClient, ctx, idempotencyKey);
    }

    @Override
    public RemoteMediaRef uploadMedia(ChannelContext ctx, MediaAssetRef asset) {
        if (asset == null) {
            throw new SocialApiException(ErrorClass.PERMANENT, "Max uploadMedia requires asset");
        }
        String type = maxUploadType(asset.kind(), asset.mime());
        String auth = token(ctx);
        try {
            JsonNode init = restClient
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/max/uploads")
                            .queryParam("type", type)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth)
                    .retrieve()
                    .body(JsonNode.class);
            if (init == null) {
                throw new SocialApiException(ErrorClass.RETRYABLE, "Max uploads empty response");
            }
            String uploadUrl = init.path("url").asText(null);
            String uploadToken = init.path("token").asText(null);
            if (uploadUrl == null || uploadUrl.isBlank()) {
                throw new SocialApiException(ErrorClass.RETRYABLE, "Max uploads missing url");
            }

            byte[] bytes = asset.hasContentBytes()
                    ? asset.contentBytes()
                    : new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
            MultipartBodyBuilder multipart = new MultipartBodyBuilder();
            multipart.part("data", new NamedByteArrayResource(bytes, fileName(asset, type)));

            String absoluteUrl = uploadUrl.startsWith("http") ? uploadUrl : baseUrl + uploadUrl;
            JsonNode uploaded = restClient
                    .post()
                    .uri(URI.create(absoluteUrl))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth)
                    .body(multipart.build())
                    .retrieve()
                    .body(JsonNode.class);

            if (uploaded != null && uploaded.has("token")) {
                uploadToken = uploaded.path("token").asText(uploadToken);
            }
            if (uploadToken == null || uploadToken.isBlank()) {
                throw new SocialApiException(ErrorClass.RETRYABLE, "Max uploads missing token");
            }
            String kind =
                    switch (type) {
                        case "video" -> "video";
                        case "audio" -> "audio";
                        case "file" -> "doc";
                        default -> "photo";
                    };
            // Prefix keeps type when publishing attachments from cache.
            return new RemoteMediaRef(type + ":" + uploadToken, kind);
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "Max upload interrupted: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public void delete(ChannelContext ctx, String remotePostId) {
        try {
            restClient
                    .delete()
                    .uri("/max/messages/{messageId}", remotePostId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(ctx))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404) {
                return;
            }
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "Max delete failed: " + ex.getMessage(), null, null, ex);
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
            if (status == 401 || status == 403) {
                return ErrorClass.AUTH;
            }
            if (status >= 500) {
                return ErrorClass.RETRYABLE;
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

    private List<String> uploadPendingMedia(ChannelContext ctx, PostVariantContent content) {
        List<MediaAssetRef> pending = PostVariantMeta.media(content == null ? Map.of() : content.meta());
        if (pending.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (MediaAssetRef asset : pending) {
            RemoteMediaRef remote = uploadMedia(ctx, asset);
            if (remote != null && remote.attachmentId() != null) {
                out.add(remote.attachmentId());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Object buildInlineKeyboard(Object buttonsRaw) {
        if (!(buttonsRaw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        List<Map<String, Object>> current = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object text = map.get("text");
            Object url = map.get("url");
            if (text == null || url == null) {
                continue;
            }
            current.add(Map.of("type", "link", "text", String.valueOf(text), "url", String.valueOf(url)));
            if (current.size() >= 3) {
                rows.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            rows.add(current);
        }
        if (rows.isEmpty()) {
            return null;
        }
        return Map.of("buttons", rows);
    }

    private PublishResult parsePublish(JsonNode body, String chatId) {
        if (body == null) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "Max empty response");
        }
        String mid = body.path("message").path("body").path("mid").asText(null);
        if (mid == null) {
            mid = body.path("message_id").asText(null);
        }
        if (mid == null) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "Max missing message id");
        }
        return new PublishResult(mid, remoteUrl(chatId, mid));
    }

    private SocialApiException mapHttpError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status == 429) {
            return new SocialApiException(
                    ErrorClass.RATE_LIMITED, "Max rate limited", status, Duration.ofSeconds(5), ex);
        }
        if (status >= 500) {
            return new SocialApiException(ErrorClass.RETRYABLE, "Max 5xx: " + status, status, null, ex);
        }
        if (status == 401 || status == 403) {
            return new SocialApiException(ErrorClass.AUTH, "Max auth failed", status, null, ex);
        }
        return new SocialApiException(ErrorClass.PERMANENT, "Max error: " + status, status, null, ex);
    }

    private static String token(ChannelContext ctx) {
        return ctx.accessToken() == null || ctx.accessToken().isBlank() ? "emulator-token" : ctx.accessToken();
    }

    private static String maxUploadType(String kind, String mime) {
        String k = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (k.contains("video") || m.startsWith("video/")) {
            return "video";
        }
        if (k.contains("audio") || m.startsWith("audio/")) {
            return "audio";
        }
        if (k.contains("doc") || k.contains("file") || m.startsWith("application/")) {
            return "file";
        }
        return "image";
    }

    private static String guessAttachmentType(String tokenRef) {
        if (tokenRef == null) {
            return "image";
        }
        int idx = tokenRef.indexOf(':');
        if (idx > 0) {
            String prefix = tokenRef.substring(0, idx);
            if (List.of("image", "video", "audio", "file").contains(prefix)) {
                return prefix;
            }
        }
        return "image";
    }

    private static String stripTypePrefix(String tokenRef) {
        if (tokenRef == null) {
            return "";
        }
        int idx = tokenRef.indexOf(':');
        if (idx > 0) {
            String prefix = tokenRef.substring(0, idx);
            if (List.of("image", "video", "audio", "file").contains(prefix)) {
                return tokenRef.substring(idx + 1);
            }
        }
        return tokenRef;
    }

    private static String fileName(MediaAssetRef asset, String type) {
        String id = asset.id() == null ? "file" : asset.id();
        return switch (type) {
            case "video" -> id + ".mp4";
            case "audio" -> id + ".mp3";
            case "file" -> id + ".bin";
            default -> id + ".jpg";
        };
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

    private static String remoteUrl(String chatId, String messageId) {
        return "https://max.ru/c/" + chatId + "/" + messageId;
    }

    private static String trimTrailingSlash(String url, String fallback) {
        if (url == null || url.isBlank()) {
            return fallback;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
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
