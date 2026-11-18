package ru.posts.social.vk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import ru.posts.social.api.PostVariantMeta;
import ru.posts.social.api.PublishResult;
import ru.posts.social.api.ReconciliationMode;
import ru.posts.social.api.RemoteMediaRef;
import ru.posts.social.api.SocialAdapter;
import ru.posts.social.api.SocialApiException;
import ru.posts.social.api.TokenRefreshRequest;
import ru.posts.social.api.TokenSet;
import ru.posts.social.vk.emulator.VkEmulatorReconciliation;

/**
 * Production VK API adapter (API 5.199): {@code wall.post} with media/options, uploads, pin,
 * comment, and UNKNOWN_OUTCOME reconciliation (BE-23 / ADR-0012).
 */
public class VkSocialAdapter implements SocialAdapter {

    private static final long PHOTO_MAX_BYTES = 50L * 1024 * 1024;
    private static final int MAX_ATTACHMENTS = 10;

    private final RestClient restClient;
    private final RestClient absoluteClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String tokenUrl;
    private final String apiVersion;
    private final ReconciliationMode reconciliationMode;
    private final int findExistingScanLimit;
    private final long findExistingTimeWindowSeconds;

    public VkSocialAdapter(VkAdapterProperties properties, ObjectMapper objectMapper) {
        this.baseUrl = trimTrailingSlash(properties.baseUrl(), "https://api.vk.com");
        this.tokenUrl = trimTrailingSlash(properties.tokenUrl(), "https://id.vk.com/oauth2/auth");
        this.apiVersion =
                properties.apiVersion() == null || properties.apiVersion().isBlank()
                        ? "5.199"
                        : properties.apiVersion();
        this.objectMapper = objectMapper;
        this.reconciliationMode = properties.reconciliation();
        this.findExistingScanLimit = Math.max(1, properties.findExistingScanLimit());
        this.findExistingTimeWindowSeconds = Math.max(1, properties.findExistingTimeWindowSeconds());
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
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("wallPost", true);
        features.put("guidIdempotency", true);
        features.put("wallPhotoUpload", true);
        features.put("wallVideoUpload", true);
        features.put("wallDocUpload", true);
        features.put("stories", true);
        features.put("pin", true);
        features.put("firstComment", true);
        features.put("closeComments", true);
        features.put("muteNotifications", true);
        features.put("signed", true);
        features.put("copyright", true);
        features.put("geo", true);
        features.put("donut", true);
        features.put("poll", true);
        features.put("carousel", true);
        features.put("grid", true);
        // VK wall.post has no co-owners / action-button params (vposter uses separate UX).
        features.put("coOwners", false);
        features.put("actionButton", false);
        features.put("clips", true);
        return new NetworkCapabilities(Network.VK, true, true, true, Map.copyOf(features));
    }

    @Override
    public PublishResult publish(ChannelContext ctx, PostVariantContent content, String idempotencyKey) {
        Map<String, Object> options = PostVariantMeta.options(content == null ? Map.of() : content.meta());
        rejectUnsupportedOptions(options);

        List<String> attachments =
                new ArrayList<>(PostVariantMeta.attachments(content == null ? Map.of() : content.meta()));
        attachments.addAll(uploadPendingMedia(ctx, content));
        if (options.get("poll") instanceof Map<?, ?> poll) {
            String pollAttachment = createPoll(ctx, poll);
            if (pollAttachment != null) {
                attachments.add(pollAttachment);
            }
        }
        if (attachments.size() > MAX_ATTACHMENTS) {
            throw new SocialApiException(
                    ErrorClass.PERMANENT, "VK allows at most " + MAX_ATTACHMENTS + " attachments per post");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("owner_id", ctx.externalId());
        form.add("from_group", "1");
        form.add("message", content == null || content.text() == null ? "" : content.text());
        form.add("guid", idempotencyKey);
        if (!attachments.isEmpty()) {
            form.add("attachments", String.join(",", attachments));
        }
        applyWallPostOptions(form, options);
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

            byte[] bytes = storyBytes(content, video);
            MultipartBodyBuilder multipart = new MultipartBodyBuilder();
            String filename = video ? "story.mp4" : "story.jpg";
            String mime = video ? "video/mp4" : "image/jpeg";
            String partName = video ? "video_file" : "file";
            multipart
                    .part(partName, new ByteArrayResource(bytes) {
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
        if (reconciliationMode == ReconciliationMode.EMULATOR) {
            return VkEmulatorReconciliation.find(restClient, ctx, idempotencyKey, false);
        }
        return findOnWall(ctx, idempotencyKey, content);
    }

    @Override
    public Optional<PublishResult> findExistingStory(
            ChannelContext ctx, String idempotencyKey, PostVariantContent content) {
        if (reconciliationMode == ReconciliationMode.EMULATOR) {
            return VkEmulatorReconciliation.find(restClient, ctx, idempotencyKey, true);
        }
        // Stories are short-lived; without a control index we cannot safely match — return empty
        // and rely on guid on re-upload where VK supports it.
        return Optional.empty();
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

    @Override
    public RemoteMediaRef uploadMedia(ChannelContext ctx, MediaAssetRef asset) {
        if (asset == null) {
            throw new SocialApiException(ErrorClass.PERMANENT, "VK uploadMedia requires asset");
        }
        String kind = normalizeKind(asset.kind(), asset.mime());
        return switch (kind) {
            case "clip" -> uploadClip(ctx, asset);
            case "video" -> uploadVideo(ctx, asset, kind);
            case "doc" -> uploadDoc(ctx, asset);
            default -> uploadPhoto(ctx, asset);
        };
    }

    @Override
    public String resolveBirthdays(ChannelContext ctx, Instant at, String timezone) {
        ZoneId zone;
        try {
            zone = timezone == null || timezone.isBlank() ? ZoneId.of("Europe/Moscow") : ZoneId.of(timezone.trim());
        } catch (Exception ex) {
            zone = ZoneId.of("Europe/Moscow");
        }
        LocalDate today = (at == null ? Instant.now() : at).atZone(zone).toLocalDate();
        List<String> names = new ArrayList<>();
        int offset = 0;
        int pageSize = 1000;
        int guard = 0;
        while (guard++ < 50) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("group_id", stripMinus(ctx.externalId()));
            form.add("sort", "id_asc");
            form.add("offset", String.valueOf(offset));
            form.add("count", String.valueOf(pageSize));
            form.add("fields", "bdate,first_name,last_name");
            form.add("access_token", token(ctx));
            form.add("v", apiVersion);
            try {
                JsonNode body = restClient
                        .post()
                        .uri("/method/groups.getMembers")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(JsonNode.class);
                assertNoVkErrorForBirthdays(body);
                JsonNode response = body.path("response");
                JsonNode items = response.path("items");
                if (!items.isArray() || items.isEmpty()) {
                    break;
                }
                for (JsonNode item : items) {
                    String bdate = item.path("bdate").asText(null);
                    if (!isBirthdayToday(bdate, today)) {
                        continue;
                    }
                    String first = item.path("first_name").asText("").trim();
                    String last = item.path("last_name").asText("").trim();
                    String display = (first + " " + last).trim();
                    if (!display.isEmpty()) {
                        names.add(display);
                    }
                }
                int count = response.path("count").asInt(items.size());
                offset += items.size();
                if (offset >= count || items.size() < pageSize) {
                    break;
                }
            } catch (SocialApiException ex) {
                throw ex;
            } catch (RestClientResponseException ex) {
                throw mapHttpError(ex);
            } catch (RestClientException ex) {
                throw new SocialApiException(
                        ErrorClass.RETRYABLE, "VK groups.getMembers failed: " + ex.getMessage(), null, null, ex);
            }
        }
        return formatBirthdays(names);
    }

    static boolean isBirthdayToday(String bdate, LocalDate today) {
        if (bdate == null || bdate.isBlank() || today == null) {
            return false;
        }
        String[] parts = bdate.trim().split("\\.");
        if (parts.length < 2) {
            return false;
        }
        try {
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            return today.getDayOfMonth() == day && today.getMonthValue() == month;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    static String formatBirthdays(List<String> displayNames) {
        if (displayNames == null || displayNames.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String name : displayNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(name.trim());
        }
        return sb.toString();
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
    public void pin(ChannelContext ctx, String remotePostId, boolean pinned) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("owner_id", ctx.externalId());
        form.add("post_id", remotePostId);
        form.add("access_token", token(ctx));
        form.add("v", apiVersion);
        String method = pinned ? "wall.pin" : "wall.unpin";
        try {
            JsonNode body = restClient
                    .post()
                    .uri("/method/" + method)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            assertNoVkError(body);
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "VK " + method + " failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public void comment(ChannelContext ctx, String remotePostId, String text) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("owner_id", ctx.externalId());
        form.add("post_id", remotePostId);
        form.add("from_group", stripMinus(ctx.externalId()));
        form.add("message", text == null ? "" : text);
        form.add("access_token", token(ctx));
        form.add("v", apiVersion);
        try {
            JsonNode body = restClient
                    .post()
                    .uri("/method/wall.createComment")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            assertNoVkError(body);
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "VK wall.createComment failed: " + ex.getMessage(), null, null, ex);
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

    private List<String> uploadPendingMedia(ChannelContext ctx, PostVariantContent content) {
        List<MediaAssetRef> media = PostVariantMeta.media(content == null ? Map.of() : content.meta());
        if (media.isEmpty()) {
            return List.of();
        }
        List<String> refs = new ArrayList<>();
        for (MediaAssetRef asset : media) {
            RemoteMediaRef remote = uploadMedia(ctx, asset);
            if (remote != null && remote.attachmentId() != null) {
                refs.add(remote.attachmentId());
            }
        }
        return refs;
    }

    private RemoteMediaRef uploadPhoto(ChannelContext ctx, MediaAssetRef asset) {
        byte[] bytes = requireBytes(asset, PHOTO_MAX_BYTES, "photo");
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

            String filename = asset.id() == null ? "photo.jpg" : asset.id() + extensionForMime(asset.mime(), ".jpg");
            String mime = asset.mime() == null ? "image/jpeg" : asset.mime();
            MultipartBodyBuilder multipart = new MultipartBodyBuilder();
            multipart
                    .part("photo", new ByteArrayResource(bytes) {
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
            return new RemoteMediaRef("photo" + ownerId + "_" + photoId, "photo");
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "VK upload failed: " + ex.getMessage(), null, null, ex);
        }
    }

    /**
     * BE-29: upload via {@code shortVideo.create} + multipart + {@code shortVideo.publish}. When the
     * method is unavailable for the app/token, fails with a clear {@code vk_clips_unavailable} error.
     */
    private RemoteMediaRef uploadClip(ChannelContext ctx, MediaAssetRef asset) {
        byte[] bytes = requireBytes(asset, 0, "clip");
        try {
            MultiValueMap<String, String> createForm = new LinkedMultiValueMap<>();
            createForm.add("group_id", stripMinus(ctx.externalId()));
            createForm.add("file_size", String.valueOf(bytes.length));
            createForm.add("wallpost", "0");
            createForm.add("access_token", token(ctx));
            createForm.add("v", apiVersion);
            JsonNode createBody = restClient
                    .post()
                    .uri("/method/shortVideo.create")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(createForm)
                    .retrieve()
                    .body(JsonNode.class);
            assertNoVkErrorForClips(createBody);
            JsonNode response = createBody.path("response");
            String uploadUrl = response.path("upload_url").asText(null);
            if (uploadUrl == null || uploadUrl.isBlank()) {
                throw new SocialApiException(ErrorClass.RETRYABLE, "VK shortVideo missing upload_url");
            }
            String filename = asset.id() == null ? "clip.mp4" : asset.id() + ".mp4";
            String mime = asset.mime() == null ? "video/mp4" : asset.mime();
            MultipartBodyBuilder multipart = new MultipartBodyBuilder();
            multipart
                    .part("file", new ByteArrayResource(bytes) {
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
            long ownerId = uploadBody == null
                    ? Long.parseLong("-" + stripMinus(ctx.externalId()))
                    : uploadBody.path("owner_id").asLong(Long.parseLong("-" + stripMinus(ctx.externalId())));
            long videoId = uploadBody == null ? 0 : uploadBody.path("video_id").asLong(0);
            if (videoId == 0) {
                videoId = response.path("video_id").asLong(0);
            }
            if (videoId == 0) {
                throw new SocialApiException(ErrorClass.RETRYABLE, "VK shortVideo missing video_id after upload");
            }
            MultiValueMap<String, String> publishForm = new LinkedMultiValueMap<>();
            publishForm.add("owner_id", String.valueOf(ownerId));
            publishForm.add("video_id", String.valueOf(videoId));
            publishForm.add("license_agree", "1");
            publishForm.add("wallpost", "0");
            publishForm.add("access_token", token(ctx));
            publishForm.add("v", apiVersion);
            JsonNode publishBody = restClient
                    .post()
                    .uri("/method/shortVideo.publish")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(publishForm)
                    .retrieve()
                    .body(JsonNode.class);
            assertNoVkErrorForClips(publishBody);
            return new RemoteMediaRef("video" + ownerId + "_" + videoId, "clip");
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "VK clip upload failed: " + ex.getMessage(), null, null, ex);
        }
    }

    private RemoteMediaRef uploadVideo(ChannelContext ctx, MediaAssetRef asset, String kind) {
        byte[] bytes = requireBytes(asset, 0, kind);
        try {
            MultiValueMap<String, String> saveForm = new LinkedMultiValueMap<>();
            saveForm.add("group_id", stripMinus(ctx.externalId()));
            saveForm.add("name", asset.id() == null ? "video" : asset.id());
            saveForm.add("wallpost", "0");
            saveForm.add("access_token", token(ctx));
            saveForm.add("v", apiVersion);
            JsonNode saveBody = restClient
                    .post()
                    .uri("/method/video.save")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(saveForm)
                    .retrieve()
                    .body(JsonNode.class);
            assertNoVkError(saveBody);
            JsonNode response = saveBody.path("response");
            String uploadUrl = response.path("upload_url").asText(null);
            if (uploadUrl == null || uploadUrl.isBlank()) {
                throw new SocialApiException(ErrorClass.RETRYABLE, "VK video missing upload_url");
            }
            String filename = asset.id() == null ? "video.mp4" : asset.id() + ".mp4";
            String mime = asset.mime() == null ? "video/mp4" : asset.mime();
            MultipartBodyBuilder multipart = new MultipartBodyBuilder();
            multipart
                    .part("video_file", new ByteArrayResource(bytes) {
                        @Override
                        public String getFilename() {
                            return filename;
                        }
                    })
                    .contentType(MediaType.parseMediaType(mime));
            absoluteClient
                    .post()
                    .uri(URI.create(uploadUrl))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart.build())
                    .retrieve()
                    .toBodilessEntity();
            long ownerId = response.path("owner_id").asLong(Long.parseLong("-" + stripMinus(ctx.externalId())));
            long videoId = response.path("video_id").asLong(response.path("vid").asLong(0));
            return new RemoteMediaRef("video" + ownerId + "_" + videoId, kind);
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "VK video upload failed: " + ex.getMessage(), null, null, ex);
        }
    }

    private RemoteMediaRef uploadDoc(ChannelContext ctx, MediaAssetRef asset) {
        byte[] bytes = requireBytes(asset, 0, "doc");
        try {
            MultiValueMap<String, String> serverForm = new LinkedMultiValueMap<>();
            serverForm.add("group_id", stripMinus(ctx.externalId()));
            serverForm.add("access_token", token(ctx));
            serverForm.add("v", apiVersion);
            JsonNode serverBody = restClient
                    .post()
                    .uri("/method/docs.getWallUploadServer")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(serverForm)
                    .retrieve()
                    .body(JsonNode.class);
            assertNoVkError(serverBody);
            String uploadUrl = serverBody.path("response").path("upload_url").asText(null);
            if (uploadUrl == null || uploadUrl.isBlank()) {
                throw new SocialApiException(ErrorClass.RETRYABLE, "VK docs missing upload_url");
            }
            String filename = asset.id() == null ? "file.bin" : asset.id() + extensionForMime(asset.mime(), ".bin");
            String mime = asset.mime() == null ? "application/octet-stream" : asset.mime();
            MultipartBodyBuilder multipart = new MultipartBodyBuilder();
            multipart
                    .part("file", new ByteArrayResource(bytes) {
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
            MultiValueMap<String, String> saveForm = new LinkedMultiValueMap<>();
            saveForm.add(
                    "file", uploadBody == null ? "" : uploadBody.path("file").asText(""));
            saveForm.add("title", filename);
            saveForm.add("access_token", token(ctx));
            saveForm.add("v", apiVersion);
            JsonNode saveBody = restClient
                    .post()
                    .uri("/method/docs.save")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(saveForm)
                    .retrieve()
                    .body(JsonNode.class);
            assertNoVkError(saveBody);
            JsonNode doc = saveBody.path("response").path("doc");
            if (doc.isMissingNode() || doc.isNull()) {
                doc = saveBody.path("response");
            }
            long ownerId = doc.path("owner_id").asLong(0);
            long docId = doc.path("id").asLong(0);
            return new RemoteMediaRef("doc" + ownerId + "_" + docId, "doc");
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "VK doc upload failed: " + ex.getMessage(), null, null, ex);
        }
    }

    private String createPoll(ChannelContext ctx, Map<?, ?> poll) {
        Object question = poll.get("question");
        Object answersRaw = poll.get("answers");
        if (question == null || answersRaw == null) {
            return null;
        }
        try {
            ArrayNode answers = objectMapper.createArrayNode();
            if (answersRaw instanceof List<?> list) {
                for (Object item : list) {
                    ObjectNode ans = objectMapper.createObjectNode();
                    ans.put("text", String.valueOf(item));
                    answers.add(ans);
                }
            }
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("owner_id", ctx.externalId());
            form.add("question", String.valueOf(question));
            form.add("add_answers", answers.toString());
            if (Boolean.TRUE.equals(poll.get("anonymous")) || "true".equals(String.valueOf(poll.get("anonymous")))) {
                form.add("is_anonymous", "1");
            }
            if (Boolean.TRUE.equals(poll.get("multiple")) || "true".equals(String.valueOf(poll.get("multiple")))) {
                form.add("is_multiple", "1");
            }
            form.add("access_token", token(ctx));
            form.add("v", apiVersion);
            JsonNode body = restClient
                    .post()
                    .uri("/method/polls.create")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            assertNoVkError(body);
            JsonNode response = body.path("response");
            long ownerId = response.path("owner_id").asLong(Long.parseLong(ctx.externalId()));
            long pollId = response.path("id").asLong(0);
            return "poll" + ownerId + "_" + pollId;
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "VK polls.create failed: " + ex.getMessage(), null, null, ex);
        }
    }

    private Optional<PublishResult> findOnWall(ChannelContext ctx, String idempotencyKey, PostVariantContent content) {
        String fingerprint = fingerprintOf(idempotencyKey, content);
        Instant now = Instant.now();
        try {
            for (String filter : List.of("all", "postponed")) {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("owner_id", ctx.externalId());
                form.add("count", String.valueOf(findExistingScanLimit));
                form.add("filter", filter);
                form.add("extended", "0");
                form.add("access_token", token(ctx));
                form.add("v", apiVersion);
                JsonNode body = restClient
                        .post()
                        .uri("/method/wall.get")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(JsonNode.class);
                assertNoVkError(body);
                JsonNode items = body.path("response").path("items");
                if (!items.isArray()) {
                    continue;
                }
                for (JsonNode item : items) {
                    if (matchesFingerprint(item, content, fingerprint, now)) {
                        String postId = item.path("id").asText(null);
                        if (postId != null) {
                            return Optional.of(new PublishResult(postId, remoteUrl(ctx.externalId(), postId)));
                        }
                    }
                }
            }
            return Optional.empty();
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private boolean matchesFingerprint(JsonNode item, PostVariantContent content, String fingerprint, Instant now) {
        String text = item.path("text").asText("");
        if (fingerprint != null && !fingerprint.isBlank() && text.contains(fingerprintMarker(fingerprint))) {
            return true;
        }
        String expected = content == null || content.text() == null ? "" : content.text();
        if (!expected.isBlank() && expected.equals(text)) {
            long date = item.path("date").asLong(0);
            if (date > 0) {
                Instant published = Instant.ofEpochSecond(date);
                long delta = Math.abs(Duration.between(published, now).toSeconds());
                return delta <= findExistingTimeWindowSeconds;
            }
            return true;
        }
        return false;
    }

    private static void applyWallPostOptions(MultiValueMap<String, String> form, Map<String, Object> options) {
        if (truthy(options.get("signed"))) {
            form.add("signed", "1");
        }
        if (truthy(options.get("mute"))) {
            form.add("mute_notifications", "1");
        }
        if (truthy(options.get("closeComments"))) {
            form.add("close_comments", "1");
        }
        Object copyright = options.get("copyrightUrl");
        if (copyright != null && !String.valueOf(copyright).isBlank()) {
            form.add("copyright", String.valueOf(copyright));
        }
        Object geo = options.get("geo");
        if (geo instanceof Map<?, ?> geoMap) {
            Object lat = geoMap.get("lat");
            Object lng = geoMap.containsKey("long") ? geoMap.get("long") : geoMap.get("lng");
            if (lat != null && lng != null) {
                form.add("lat", String.valueOf(lat));
                form.add("long", String.valueOf(lng));
            }
        }
        Object donut = options.get("donutDays");
        if (donut instanceof Number n && n.intValue() > 0) {
            form.add("donut_paid_duration", String.valueOf(n.intValue()));
        } else if (donut != null) {
            try {
                int days = Integer.parseInt(String.valueOf(donut));
                if (days > 0) {
                    form.add("donut_paid_duration", String.valueOf(days));
                }
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        Object mode = options.get("primaryAttachmentsMode");
        if (mode == null) {
            mode = options.get("carouselMode");
        }
        if (mode != null) {
            String normalized = String.valueOf(mode).trim().toLowerCase(Locale.ROOT);
            if ("carousel".equals(normalized) || "grid".equals(normalized)) {
                form.add("primary_attachments_mode", normalized);
            }
        }
        // publish_date intentionally omitted — our scheduler owns timing (02-backend.md §5.6).
    }

    private void rejectUnsupportedOptions(Map<String, Object> options) {
        Object coOwners = options.get("coOwnerIds");
        if (coOwners instanceof List<?> list && !list.isEmpty()) {
            throw new SocialApiException(
                    ErrorClass.PERMANENT,
                    "VK wall.post does not support co-owners (capability coOwners=false); remove coOwnerIds");
        }
        Object actionButton = options.get("actionButton");
        if (actionButton instanceof Map<?, ?> map && !map.isEmpty()) {
            throw new SocialApiException(
                    ErrorClass.PERMANENT,
                    "VK wall.post does not support action buttons (capability actionButton=false)");
        }
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

    private void assertNoVkErrorForClips(JsonNode body) {
        if (body == null) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "VK empty response");
        }
        JsonNode error = body.get("error");
        if (error == null || error.isNull()) {
            return;
        }
        int code = error.path("error_code").asInt(0);
        String msg = error.path("error_msg").asText("VK error " + code);
        // 3 = unknown method; 15/203 = access denied — clips API not available for this app/token.
        if (code == 3
                || code == 15
                || code == 203
                || msg.toLowerCase(Locale.ROOT).contains("unknown method")) {
            throw new SocialApiException(
                    ErrorClass.PERMANENT,
                    "vk_clips_unavailable: VK shortVideo/clips API is not available for this token or app ("
                            + msg
                            + ")",
                    422);
        }
        ErrorClass clazz = classifyVkErrorCode(code);
        Duration retryAfter = clazz == ErrorClass.RATE_LIMITED ? Duration.ofHours(1) : null;
        throw new SocialApiException(clazz, msg, null, retryAfter, null);
    }

    private void assertNoVkErrorForBirthdays(JsonNode body) {
        if (body == null) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "VK empty response");
        }
        JsonNode error = body.get("error");
        if (error == null || error.isNull()) {
            return;
        }
        int code = error.path("error_code").asInt(0);
        String msg = error.path("error_msg").asText("VK error " + code);
        if (code == 15 || code == 203 || code == 212 || code == 260 || code == 7) {
            throw new SocialApiException(
                    ErrorClass.PERMANENT,
                    "vk_birthdays_forbidden: community token lacks rights to read members' birthdays via "
                            + "groups.getMembers (bdate). Grant members access or remove {birthdays}/{именинники}. "
                            + "("
                            + msg
                            + ")",
                    403);
        }
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
            case 5, 15, 203, 212 -> ErrorClass.AUTH;
            case 14 -> ErrorClass.PERMANENT; // captcha
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

    private static byte[] storyBytes(PostVariantContent content, boolean video) {
        List<MediaAssetRef> media = PostVariantMeta.media(content == null ? Map.of() : content.meta());
        if (!media.isEmpty() && media.getFirst().hasContentBytes()) {
            return media.getFirst().contentBytes();
        }
        return video
                ? new byte[] {0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70}
                : new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
    }

    private static byte[] requireBytes(MediaAssetRef asset, long maxBytes, String kind) {
        if (!asset.hasContentBytes()) {
            throw new SocialApiException(
                    ErrorClass.PERMANENT, "VK " + kind + " upload requires content bytes (load from S3 first)");
        }
        byte[] bytes = asset.contentBytes();
        if (maxBytes > 0 && bytes.length > maxBytes) {
            throw new SocialApiException(
                    ErrorClass.PERMANENT, "VK " + kind + " exceeds size limit of " + maxBytes + " bytes");
        }
        return bytes;
    }

    private static String normalizeKind(String kind, String mime) {
        String k = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        if (k.contains("video") || k.contains("clip")) {
            return k.contains("clip") ? "clip" : "video";
        }
        if (k.contains("doc") || k.contains("file")) {
            return "doc";
        }
        if (k.contains("audio")) {
            return "doc";
        }
        if (mime != null) {
            String m = mime.toLowerCase(Locale.ROOT);
            if (m.startsWith("video/")) {
                return "video";
            }
            if (m.startsWith("image/")) {
                return "photo";
            }
        }
        return "photo";
    }

    private static String extensionForMime(String mime, String fallback) {
        if (mime == null) {
            return fallback;
        }
        return switch (mime.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "application/pdf" -> ".pdf";
            default -> fallback;
        };
    }

    private static String fingerprintOf(String idempotencyKey, PostVariantContent content) {
        String fromMeta = PostVariantMeta.fingerprint(content == null ? Map.of() : content.meta());
        if (fromMeta != null && !fromMeta.isBlank()) {
            return fromMeta;
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(idempotencyKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception ex) {
            return idempotencyKey;
        }
    }

    /** Invisible marker appended by publisher for prod reconciliation when configured. */
    public static String fingerprintMarker(String fingerprint) {
        return "\u200b" + fingerprint + "\u200b";
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return false;
        }
        String s = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
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
