package ru.posts.social.vk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
import ru.posts.social.api.TokenRefreshRequest;
import ru.posts.social.api.TokenSet;

/**
 * VK API adapter ({@code wall.post} with {@code guid} idempotency). Targets real VK or the social
 * emulator via configurable {@code baseUrl}.
 */
public class VkSocialAdapter implements SocialAdapter {

    private final RestClient restClient;
    private final RestClient absoluteClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String tokenUrl;
    private final String apiVersion;

    public VkSocialAdapter(VkAdapterProperties properties, ObjectMapper objectMapper) {
        this.baseUrl = trimTrailingSlash(properties.baseUrl(), "https://api.vk.com");
        this.tokenUrl = trimTrailingSlash(properties.tokenUrl(), "https://id.vk.com/oauth2/auth");
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
        this.absoluteClient =
                RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public Network network() {
        return Network.VK;
    }

    @Override
    public NetworkCapabilities capabilities() {
        return new NetworkCapabilities(
                Network.VK,
                true,
                true,
                true,
                Map.of("wallPost", true, "guidIdempotency", true, "wallPhotoUpload", true, "stories", true));
    }

    @Override
    public PublishResult publish(ChannelContext ctx, PostVariantContent content, String idempotencyKey) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("owner_id", ctx.externalId());
        form.add("message", content == null || content.text() == null ? "" : content.text());
        form.add("guid", idempotencyKey);
        form.add("access_token", token(ctx));
        form.add("v", apiVersion);
        try {
            JsonNode body = restClient
                    .post()
                    .uri("/method/wall.post")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            return parseWallPost(body, ctx.externalId());
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            throw new SocialApiException(
                    ErrorClass.UNKNOWN_OUTCOME, "VK call interrupted: " + ex.getMessage(), null, null, ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "VK transport error: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public PublishResult publishStory(ChannelContext ctx, PostVariantContent content, String idempotencyKey) {
        boolean video = content != null && content.isVideo();
        String uploadMethod = video ? "stories.getVideoUploadServer" : "stories.getPhotoUploadServer";
        String saveMethod = "stories.save";
        try {
            MultiValueMap<String, String> serverForm = new LinkedMultiValueMap<>();
            serverForm.add("group_id", stripMinus(ctx.externalId()));
            serverForm.add("add_to_news", "1");
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                serverForm.add("guid", idempotencyKey);
            }
            if (content != null
                    && content.linkUrl() != null
                    && !content.linkUrl().isBlank()) {
                serverForm.add("link_url", content.linkUrl());
                if (content.linkText() != null && !content.linkText().isBlank()) {
                    serverForm.add("link_text", content.linkText());
                }
            }
            serverForm.add("access_token", token(ctx));
            serverForm.add("v", apiVersion);
            JsonNode serverBody = restClient
                    .post()
                    .uri("/method/" + uploadMethod)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(serverForm)
                    .retrieve()
                    .body(JsonNode.class);
            assertNoVkError(serverBody);
            String uploadUrl = serverBody.path("response").path("upload_url").asText(null);
            if (uploadUrl == null || uploadUrl.isBlank()) {
                throw new SocialApiException(ErrorClass.RETRYABLE, "VK stories missing upload_url");
            }

            MultipartBodyBuilder multipart = new MultipartBodyBuilder();
            String filename = video ? "story.mp4" : "story.jpg";
            String mime = video ? "video/mp4" : "image/jpeg";
            byte[] stubBytes = video
                    ? new byte[] {0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70}
                    : new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
            String partName = video ? "video_file" : "file";
            multipart
                    .part(partName, new ByteArrayResource(stubBytes) {
                        @Override
                        public String getFilename() {
                            return filename;
                        }
                    })
                    .contentType(MediaType.parseMediaType(mime));

            JsonNode uploadBody = absoluteClient
                    .post()
                    .uri(URI.create(uploadUrl))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart.build())
                    .retrieve()
                    .body(JsonNode.class);
            if (uploadBody == null) {
                throw new SocialApiException(ErrorClass.RETRYABLE, "VK story upload empty response");
            }

            MultiValueMap<String, String> saveForm = new LinkedMultiValueMap<>();
            saveForm.add("upload_results", uploadBody.toString());
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                saveForm.add("guid", idempotencyKey);
            }
            saveForm.add("access_token", token(ctx));
            saveForm.add("v", apiVersion);
            JsonNode saveBody = restClient
                    .post()
                    .uri("/method/" + saveMethod)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(saveForm)
                    .retrieve()
                    .body(JsonNode.class);
            return parseStorySave(saveBody, ctx.externalId());
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            throw new SocialApiException(
                    ErrorClass.UNKNOWN_OUTCOME, "VK story call interrupted: " + ex.getMessage(), null, null, ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "VK story transport error: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public Optional<PublishResult> findExisting(ChannelContext ctx, String idempotencyKey, PostVariantContent content) {
        return findInControl(ctx, idempotencyKey, false);
    }

    @Override
    public Optional<PublishResult> findExistingStory(
            ChannelContext ctx, String idempotencyKey, PostVariantContent content) {
        return findInControl(ctx, idempotencyKey, true);
    }

    @Override
    public void deleteStory(ChannelContext ctx, String remoteStoryId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("owner_id", ctx.externalId());
        form.add("story_id", remoteStoryId);
        form.add("access_token", token(ctx));
        form.add("v", apiVersion);
        try {
            JsonNode body = restClient
                    .post()
                    .uri("/method/stories.delete")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            assertNoVkError(body);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404) {
                return;
            }
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "VK story delete failed: " + ex.getMessage(), null, null, ex);
        }
    }

    private Optional<PublishResult> findInControl(ChannelContext ctx, String idempotencyKey, boolean story) {
        try {
            JsonNode posts = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/_control/posts")
                            .queryParam("network", "vk")
                            .queryParam("channelId", ctx.externalId())
                            .queryParam("contentType", story ? "story" : "post")
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
                        String url =
                                story ? storyUrl(ctx.externalId(), remoteId) : remoteUrl(ctx.externalId(), remoteId);
                        return Optional.of(new PublishResult(remoteId, url));
                    }
                }
            }
            return Optional.empty();
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    @Override
    public RemoteMediaRef uploadMedia(ChannelContext ctx, MediaAssetRef asset) {
        try {
            MultiValueMap<String, String> serverForm = new LinkedMultiValueMap<>();
            serverForm.add("group_id", stripMinus(ctx.externalId()));
            serverForm.add("access_token", token(ctx));
            serverForm.add("v", apiVersion);
            JsonNode serverBody = restClient
                    .post()
                    .uri("/method/photos.getWallUploadServer")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(serverForm)
                    .retrieve()
                    .body(JsonNode.class);
            assertNoVkError(serverBody);
            String uploadUrl = serverBody.path("response").path("upload_url").asText(null);
            if (uploadUrl == null || uploadUrl.isBlank()) {
                throw new SocialApiException(ErrorClass.RETRYABLE, "VK missing upload_url");
            }

            MultipartBodyBuilder multipart = new MultipartBodyBuilder();
            String filename = asset == null || asset.id() == null ? "photo.jpg" : asset.id() + ".jpg";
            String mime = asset == null || asset.mime() == null ? "image/jpeg" : asset.mime();
            byte[] stubBytes = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
            multipart
                    .part("photo", new ByteArrayResource(stubBytes) {
                        @Override
                        public String getFilename() {
                            return filename;
                        }
                    })
                    .contentType(MediaType.parseMediaType(mime));

            JsonNode uploadBody = absoluteClient
                    .post()
                    .uri(URI.create(uploadUrl))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart.build())
                    .retrieve()
                    .body(JsonNode.class);
            if (uploadBody == null) {
                throw new SocialApiException(ErrorClass.RETRYABLE, "VK upload empty response");
            }

            MultiValueMap<String, String> saveForm = new LinkedMultiValueMap<>();
            saveForm.add("group_id", stripMinus(ctx.externalId()));
            saveForm.add("photo", uploadBody.path("photo").asText(""));
            saveForm.add("server", uploadBody.path("server").asText("1"));
            saveForm.add("hash", uploadBody.path("hash").asText(""));
            saveForm.add("access_token", token(ctx));
            saveForm.add("v", apiVersion);
            JsonNode saveBody = restClient
                    .post()
                    .uri("/method/photos.saveWallPhoto")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(saveForm)
                    .retrieve()
                    .body(JsonNode.class);
            assertNoVkError(saveBody);
            JsonNode photo = saveBody.path("response").isArray()
                            && saveBody.path("response").size() > 0
                    ? saveBody.path("response").get(0)
                    : saveBody.path("response");
            long ownerId = photo.path("owner_id").asLong(0);
            long photoId = photo.path("id").asLong(0);
            String attachmentId = "photo" + ownerId + "_" + photoId;
            String kind = asset == null || asset.kind() == null ? "photo" : asset.kind();
            return new RemoteMediaRef(attachmentId, kind);
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "VK upload failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public void delete(ChannelContext ctx, String remotePostId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("owner_id", ctx.externalId());
        form.add("post_id", remotePostId);
        form.add("access_token", token(ctx));
        form.add("v", apiVersion);
        try {
            JsonNode body = restClient
                    .post()
                    .uri("/method/wall.delete")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            assertNoVkError(body);
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "VK delete failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public Optional<TokenSet> refreshToken(TokenRefreshRequest request) {
        if (request == null
                || request.refreshToken() == null
                || request.refreshToken().isBlank()) {
            return Optional.empty();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", request.refreshToken());
        try {
            JsonNode body = absoluteClient
                    .post()
                    .uri(URI.create(tokenUrl))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || !body.has("access_token")) {
                return Optional.empty();
            }
            String access = body.path("access_token").asText(null);
            String refresh = body.path("refresh_token").asText(request.refreshToken());
            Instant expiresAt = null;
            if (body.has("expires_in")) {
                expiresAt = Instant.now().plusSeconds(body.path("expires_in").asLong());
            }
            return Optional.of(new TokenSet(access, refresh, expiresAt));
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "VK token refresh failed: " + ex.getMessage(), null, null, ex);
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

    private PublishResult parseWallPost(JsonNode body, String ownerId) {
        assertNoVkError(body);
        JsonNode response = body.path("response");
        String postId = response.path("post_id").asText(null);
        if (postId == null) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "VK missing post_id");
        }
        return new PublishResult(postId, remoteUrl(ownerId, postId));
    }

    private PublishResult parseStorySave(JsonNode body, String ownerId) {
        assertNoVkError(body);
        JsonNode response = body.path("response");
        JsonNode items = response.path("items");
        String storyId = null;
        if (items.isArray() && !items.isEmpty()) {
            storyId = items.get(0).path("id").asText(null);
            if (storyId == null) {
                storyId = items.get(0).path("story_id").asText(null);
            }
        }
        if (storyId == null) {
            storyId = response.path("story_id").asText(null);
        }
        if (storyId == null) {
            storyId = response.path("id").asText(null);
        }
        if (storyId == null) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "VK missing story_id");
        }
        return new PublishResult(storyId, storyUrl(ownerId, storyId));
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
        ErrorClass clazz = classifyVkErrorCode(code);
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
                return classifyVkErrorCode(error.path("error_code").asInt());
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private static ErrorClass classifyVkErrorCode(int code) {
        return switch (code) {
            case 6, 9 -> ErrorClass.RATE_LIMITED;
            case 10, 1 -> ErrorClass.RETRYABLE;
            case 5, 15 -> ErrorClass.AUTH;
            default -> ErrorClass.PERMANENT;
        };
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
        if (status == 401 || status == 403) {
            return new SocialApiException(ErrorClass.AUTH, "VK auth failed", status, null, ex);
        }
        ErrorClass fromBody = classifyVkErrorBody(ex.getResponseBodyAsString());
        if (fromBody != null) {
            return new SocialApiException(fromBody, "VK error", status, null, ex);
        }
        return new SocialApiException(ErrorClass.PERMANENT, "VK error: " + status, status, null, ex);
    }

    private static String token(ChannelContext ctx) {
        return ctx.accessToken() == null || ctx.accessToken().isBlank() ? "emulator-token" : ctx.accessToken();
    }

    private static String remoteUrl(String ownerId, String postId) {
        return "https://vk.com/wall" + ownerId + "_" + postId;
    }

    private static String storyUrl(String ownerId, String storyId) {
        return "https://vk.com/story" + ownerId + "_" + storyId;
    }

    private static String stripMinus(String ownerId) {
        if (ownerId == null) {
            return "0";
        }
        return ownerId.startsWith("-") ? ownerId.substring(1) : ownerId;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String trimTrailingSlash(String url, String fallback) {
        if (url == null || url.isBlank()) {
            return fallback;
        }
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
