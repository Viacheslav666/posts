package ru.posts.social.ok;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
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
import ru.posts.social.ok.emulator.OkEmulatorReconciliation;

/**
 * OK (Odnoklassniki) adapter: {@code mediatopic.post} with JSON attachment, media upload, optional
 * sig, and UNKNOWN_OUTCOME reconciliation (BE-23 / ADR-0012).
 */
public class OkSocialAdapter implements SocialAdapter {

    private static final long OK_MOMENT_TTL_MS = 86_400_000L;

    private final RestClient restClient;
    private final RestClient absoluteClient;
    private final ObjectMapper objectMapper;
    private final String applicationKey;
    private final String applicationSecret;
    private final ReconciliationMode reconciliationMode;
    private final int findExistingScanLimit;

    public OkSocialAdapter(OkAdapterProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.applicationKey = properties.applicationKey() == null ? "" : properties.applicationKey();
        this.applicationSecret = properties.applicationSecret() == null ? "" : properties.applicationSecret();
        this.reconciliationMode = properties.reconciliation();
        this.findExistingScanLimit = Math.max(1, properties.findExistingScanLimit());
        String baseUrl = trimTrailingSlash(properties.baseUrl(), "https://api.ok.ru");
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
        return Network.OK;
    }

    @Override
    public NetworkCapabilities capabilities() {
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("mediatopicPost", true);
        features.put("guidIdempotency", true);
        features.put("stories", true);
        features.put("moments", true);
        features.put("uploadMedia", true);
        features.put("disableComments", true);
        features.put("poll", true);
        // Official OK REST docs do not expose wall pin / first-comment for groups.
        features.put("pin", false);
        features.put("firstComment", false);
        features.put("coOwners", false);
        features.put("actionButton", false);
        features.put("donut", false);
        return new NetworkCapabilities(Network.OK, true, true, false, Map.copyOf(features));
    }

    @Override
    public PublishResult publish(ChannelContext ctx, PostVariantContent content, String idempotencyKey) {
        Map<String, Object> options = PostVariantMeta.options(content == null ? Map.of() : content.meta());
        rejectUnsupportedOptions(options);

        try {
            String attachmentJson = buildAttachmentJson(ctx, content, options);
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("method", "mediatopic.post");
            form.add("gid", ctx.externalId());
            form.add("attachment", attachmentJson);
            form.add("idempotency_key", idempotencyKey == null ? "" : idempotencyKey);
            if (truthy(options.get("closeComments"))) {
                form.add("disable_comments", "true");
            }
            applySignedForm(form, token(ctx));
            JsonNode body = restClient
                    .post()
                    .uri("/ok/fb.do")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            return parsePublish(body, ctx.externalId());
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            throw new SocialApiException(
                    ErrorClass.UNKNOWN_OUTCOME, "OK call interrupted: " + ex.getMessage(), null, null, ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "OK transport error: " + ex.getMessage(), null, null, ex);
        } catch (Exception ex) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "OK publish failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public PublishResult publishStory(ChannelContext ctx, PostVariantContent content, String idempotencyKey) {
        boolean video = content != null && content.isVideo();
        String uploadType = video ? "VIDEO" : "PHOTO";
        try {
            MultiValueMap<String, String> uploadForm = new LinkedMultiValueMap<>();
            uploadForm.add("method", "moments.getUploadUrl");
            uploadForm.add("gid", ctx.externalId());
            uploadForm.add("count", "1");
            uploadForm.add("type", uploadType);
            applySignedForm(uploadForm, token(ctx));
            JsonNode uploadUrlBody = restClient
                    .post()
                    .uri("/ok/fb.do")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(uploadForm)
                    .retrieve()
                    .body(JsonNode.class);
            assertNoOkError(uploadUrlBody);
            String uploadUrl = firstUploadUrl(uploadUrlBody);
            if (uploadUrl == null || uploadUrl.isBlank()) {
                throw new SocialApiException(ErrorClass.RETRYABLE, "OK moments missing upload_url");
            }

            byte[] bytes = storyBytes(content, video);
            MultipartBodyBuilder multipart = new MultipartBodyBuilder();
            String filename = video ? "moment.mp4" : "moment.jpg";
            String mime = video ? "video/mp4" : "image/jpeg";
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

            MultiValueMap<String, String> commitForm = new LinkedMultiValueMap<>();
            commitForm.add("method", "moments.commit");
            commitForm.add("owner_ref", "group:" + ctx.externalId());
            commitForm.add("ttl_ms", String.valueOf(OK_MOMENT_TTL_MS));
            commitForm.add("idempotency_key", idempotencyKey == null ? "" : idempotencyKey);
            if (content != null && content.text() != null) {
                commitForm.add("text", content.text());
            }
            if (content != null
                    && content.linkUrl() != null
                    && !content.linkUrl().isBlank()) {
                commitForm.add("link_url", content.linkUrl());
                if (content.linkText() != null) {
                    commitForm.add("link_text", content.linkText());
                }
            }
            if (uploadBody != null) {
                commitForm.add("upload_token", uploadBody.path("token").asText(uploadBody.toString()));
            }
            applySignedForm(commitForm, token(ctx));
            JsonNode commitBody = restClient
                    .post()
                    .uri("/ok/fb.do")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(commitForm)
                    .retrieve()
                    .body(JsonNode.class);
            return parseMomentCommit(commitBody, ctx.externalId());
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            throw new SocialApiException(
                    ErrorClass.UNKNOWN_OUTCOME, "OK moment call interrupted: " + ex.getMessage(), null, null, ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "OK moment transport error: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public Optional<PublishResult> findExisting(ChannelContext ctx, String idempotencyKey, PostVariantContent content) {
        if (reconciliationMode == ReconciliationMode.EMULATOR) {
            return OkEmulatorReconciliation.find(restClient, ctx, idempotencyKey, false);
        }
        return findInFeed(ctx, idempotencyKey, content);
    }

    @Override
    public Optional<PublishResult> findExistingStory(
            ChannelContext ctx, String idempotencyKey, PostVariantContent content) {
        if (reconciliationMode == ReconciliationMode.EMULATOR) {
            return OkEmulatorReconciliation.find(restClient, ctx, idempotencyKey, true);
        }
        return Optional.empty();
    }

    @Override
    public RemoteMediaRef uploadMedia(ChannelContext ctx, MediaAssetRef asset) {
        if (asset == null || !asset.hasContentBytes()) {
            throw new SocialApiException(ErrorClass.PERMANENT, "OK uploadMedia requires content bytes");
        }
        String kind = normalizeKind(asset.kind(), asset.mime());
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("method", "photosV2.getUploadUrl");
            form.add("gid", ctx.externalId());
            form.add("count", "1");
            applySignedForm(form, token(ctx));
            JsonNode serverBody = restClient
                    .post()
                    .uri("/ok/fb.do")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            assertNoOkError(serverBody);
            String uploadUrl = firstUploadUrl(serverBody);
            if (uploadUrl == null || uploadUrl.isBlank()) {
                // Emulator / fallback: photos.getUploadUrl style
                throw new SocialApiException(ErrorClass.RETRYABLE, "OK missing upload_url");
            }
            String filename = asset.id() == null ? "file.bin" : asset.id();
            String mime = asset.mime() == null ? "application/octet-stream" : asset.mime();
            MultipartBodyBuilder multipart = new MultipartBodyBuilder();
            multipart
                    .part("file", new ByteArrayResource(asset.contentBytes()) {
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
            String token = uploadBody == null
                    ? null
                    : uploadBody.path("photos").path("photo").path("token").asText(null);
            if (token == null && uploadBody != null) {
                token = uploadBody.path("token").asText(null);
            }
            if (token == null
                    && uploadBody != null
                    && uploadBody.path("photo_ids").isArray()
                    && !uploadBody.path("photo_ids").isEmpty()) {
                token = uploadBody.path("photo_ids").get(0).path("token").asText(null);
            }
            if (token == null) {
                token = "ok-photo-" + (asset.id() == null ? "x" : asset.id());
            }
            return new RemoteMediaRef(token, kind);
        } catch (SocialApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "OK upload failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public void deleteStory(ChannelContext ctx, String remoteStoryId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("method", "moments.delete");
        form.add("moment_id", remoteStoryId);
        form.add("gid", ctx.externalId());
        applySignedForm(form, token(ctx));
        try {
            restClient
                    .post()
                    .uri("/ok/fb.do")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404) {
                return;
            }
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "OK moment delete failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public void delete(ChannelContext ctx, String remotePostId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("method", "mediatopic.delete");
        form.add("topic_id", remotePostId);
        form.add("gid", ctx.externalId());
        applySignedForm(form, token(ctx));
        try {
            restClient
                    .post()
                    .uri("/ok/fb.do")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404) {
                return;
            }
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "OK delete failed: " + ex.getMessage(), null, null, ex);
        }
    }

    @Override
    public Optional<Long> fetchImpressions(ChannelContext ctx, String remotePostId) {
        if (remotePostId == null || remotePostId.isBlank()) {
            return Optional.empty();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("method", "mediatopic.getInfo");
        form.add("topic_ids", remotePostId);
        form.add("fields", "media_topic.*");
        applySignedForm(form, token(ctx));
        try {
            JsonNode body = restClient
                    .post()
                    .uri("/ok/fb.do")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode mediaTopics = body.path("media_topics");
            if (!mediaTopics.isArray() || mediaTopics.isEmpty()) {
                JsonNode single = body.path("media_topic");
                if (!single.isMissingNode() && !single.isNull()) {
                    long views = single.path("views_count").asLong(-1);
                    if (views < 0) {
                        views = single.path("views").asLong(-1);
                    }
                    return views >= 0 ? Optional.of(views) : Optional.empty();
                }
                return Optional.empty();
            }
            long views = mediaTopics.get(0).path("views_count").asLong(-1);
            if (views < 0) {
                views = mediaTopics.get(0).path("views").asLong(-1);
            }
            return views >= 0 ? Optional.of(views) : Optional.empty();
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void pin(ChannelContext ctx, String remotePostId, boolean pinned) {
        // capability pin=false — treat as no-op success for scheduled_actions idempotency
    }

    @Override
    public void comment(ChannelContext ctx, String remotePostId, String text) {
        // capability firstComment=false — no-op
    }

    @Override
    public Optional<TokenSet> refreshToken(TokenRefreshRequest request) {
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
            if (status == 401 || status == 403) {
                return ErrorClass.AUTH;
            }
            if (status >= 500) {
                return ErrorClass.RETRYABLE;
            }
            if (status == 499) {
                return ErrorClass.UNKNOWN_OUTCOME;
            }
            ErrorClass fromBody = classifyOkBody(http.getResponseBodyAsString());
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

    private String buildAttachmentJson(ChannelContext ctx, PostVariantContent content, Map<String, Object> options)
            throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode media = root.putArray("media");
        String text = content == null || content.text() == null ? "" : content.text();
        if (!text.isBlank()) {
            ObjectNode textBlock = media.addObject();
            textBlock.put("type", "text");
            textBlock.put("text", text);
        }
        List<String> attachmentTokens =
                new ArrayList<>(PostVariantMeta.attachments(content == null ? Map.of() : content.meta()));
        for (MediaAssetRef asset : PostVariantMeta.media(content == null ? Map.of() : content.meta())) {
            RemoteMediaRef remote = uploadMedia(ctx, asset);
            if (remote != null && remote.attachmentId() != null) {
                attachmentTokens.add(remote.attachmentId());
            }
        }
        for (String token : attachmentTokens) {
            ObjectNode block = media.addObject();
            String kind = token.startsWith("video") || token.startsWith("movie") ? "movie" : "photo";
            block.put("type", kind);
            ArrayNode listArr = block.putArray("list");
            ObjectNode item = listArr.addObject();
            item.put("id", token);
        }
        if (options.get("poll") instanceof Map<?, ?> poll) {
            ObjectNode pollBlock = media.addObject();
            pollBlock.put("type", "poll");
            Object question = poll.get("question");
            pollBlock.put("question", question == null ? "" : String.valueOf(question));
            ArrayNode answers = pollBlock.putArray("answers");
            Object answersRaw = poll.get("answers");
            if (answersRaw instanceof List<?> list) {
                for (Object a : list) {
                    answers.add(String.valueOf(a));
                }
            }
        }
        return objectMapper.writeValueAsString(root);
    }

    private Optional<PublishResult> findInFeed(ChannelContext ctx, String idempotencyKey, PostVariantContent content) {
        String fingerprint = fingerprintOf(idempotencyKey, content);
        String expected = content == null || content.text() == null ? "" : content.text();
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("method", "stream.get");
            form.add("gid", ctx.externalId());
            form.add("count", String.valueOf(findExistingScanLimit));
            applySignedForm(form, token(ctx));
            JsonNode body = restClient
                    .post()
                    .uri("/ok/fb.do")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            assertNoOkError(body);
            JsonNode entities = body.path("entities").path("topics");
            if (!entities.isObject()) {
                entities = body.path("topics");
            }
            if (entities.isObject()) {
                var fields = entities.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    JsonNode topic = entry.getValue();
                    String text = topic.path("media").isArray()
                                    && !topic.path("media").isEmpty()
                            ? topic.path("media").get(0).path("text").asText("")
                            : topic.path("text").asText("");
                    if (matches(text, expected, fingerprint)) {
                        String id = topic.path("id").asText(entry.getKey());
                        return Optional.of(new PublishResult(id, remoteUrl(ctx.externalId(), id)));
                    }
                }
            }
            JsonNode mediaTopics = body.path("media_topics");
            if (mediaTopics.isArray()) {
                for (JsonNode topic : mediaTopics) {
                    String text = topic.path("text").asText("");
                    if (matches(text, expected, fingerprint)) {
                        String id = topic.path("id").asText(null);
                        if (id != null) {
                            return Optional.of(new PublishResult(id, remoteUrl(ctx.externalId(), id)));
                        }
                    }
                }
            }
            return Optional.empty();
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static boolean matches(String text, String expected, String fingerprint) {
        if (fingerprint != null && !fingerprint.isBlank() && text != null && text.contains(fingerprint)) {
            return true;
        }
        return expected != null && !expected.isBlank() && expected.equals(text);
    }

    private void applySignedForm(MultiValueMap<String, String> form, String accessToken) {
        if (!applicationKey.isBlank()) {
            form.add("application_key", applicationKey);
        }
        form.add("access_token", accessToken);
        form.add("format", "json");
        if (!applicationSecret.isBlank()) {
            form.set("sig", sign(form, accessToken));
        }
    }

    private String sign(MultiValueMap<String, String> form, String accessToken) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, List<String>> e : form.entrySet()) {
            if ("sig".equals(e.getKey()) || "access_token".equals(e.getKey())) {
                continue;
            }
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                sorted.put(e.getKey(), e.getValue().getFirst());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        String sessionSecret = md5(accessToken + applicationSecret);
        return md5(sb + sessionSecret);
    }

    private static String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void rejectUnsupportedOptions(Map<String, Object> options) {
        Object coOwners = options.get("coOwnerIds");
        if (coOwners instanceof List<?> list && !list.isEmpty()) {
            throw new SocialApiException(
                    ErrorClass.PERMANENT, "OK does not support co-owners (capability coOwners=false)");
        }
        Object actionButton = options.get("actionButton");
        if (actionButton instanceof Map<?, ?> map && !map.isEmpty()) {
            throw new SocialApiException(
                    ErrorClass.PERMANENT, "OK does not support action buttons (capability actionButton=false)");
        }
        Object donut = options.get("donutDays");
        if (donut instanceof Number n && n.intValue() > 0) {
            throw new SocialApiException(ErrorClass.PERMANENT, "OK does not support Donut (capability donut=false)");
        }
    }

    private PublishResult parsePublish(JsonNode body, String gid) {
        assertNoOkError(body);
        String id = body.path("id").asText(null);
        if (id == null) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "OK missing id");
        }
        return new PublishResult(id, remoteUrl(gid, id));
    }

    private PublishResult parseMomentCommit(JsonNode body, String gid) {
        assertNoOkError(body);
        String id = body.path("moment_id").asText(null);
        if (id == null) {
            id = body.path("id").asText(null);
        }
        if (id == null) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "OK missing moment_id");
        }
        return new PublishResult(id, momentUrl(gid, id));
    }

    private void assertNoOkError(JsonNode body) {
        if (body == null) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "OK empty response");
        }
        if (body.has("error_code")) {
            int code = body.path("error_code").asInt();
            throw new SocialApiException(
                    classifyOkErrorCode(code), body.path("error_msg").asText("OK error " + code));
        }
    }

    private static String firstUploadUrl(JsonNode body) {
        if (body == null) {
            return null;
        }
        String direct = body.path("upload_url").asText(null);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        JsonNode urls = body.path("upload_urls");
        if (urls.isArray() && !urls.isEmpty()) {
            return urls.get(0).asText(null);
        }
        JsonNode photoIds = body.path("photo_ids");
        if (photoIds.isArray() && !photoIds.isEmpty()) {
            return photoIds.get(0).path("upload_url").asText(null);
        }
        return null;
    }

    private ErrorClass classifyOkBody(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode body = objectMapper.readTree(raw);
            if (body.has("error_code")) {
                return classifyOkErrorCode(body.path("error_code").asInt());
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private static ErrorClass classifyOkErrorCode(int code) {
        return switch (code) {
            case 1, 9 -> ErrorClass.RETRYABLE;
            case 2, 102, 103 -> ErrorClass.AUTH;
            case 4 -> ErrorClass.RATE_LIMITED;
            case 8 -> ErrorClass.PERMANENT; // captcha / permission
            default -> ErrorClass.PERMANENT;
        };
    }

    private SocialApiException mapHttpError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status == 429) {
            return new SocialApiException(
                    ErrorClass.RATE_LIMITED, "OK rate limited", status, Duration.ofSeconds(5), ex);
        }
        if (status >= 500) {
            return new SocialApiException(ErrorClass.RETRYABLE, "OK 5xx: " + status, status, null, ex);
        }
        if (status == 401 || status == 403) {
            return new SocialApiException(ErrorClass.AUTH, "OK auth failed", status, null, ex);
        }
        if (status == 499) {
            return new SocialApiException(ErrorClass.UNKNOWN_OUTCOME, "OK response lost", status, null, ex);
        }
        ErrorClass fromBody = classifyOkBody(ex.getResponseBodyAsString());
        if (fromBody != null) {
            return new SocialApiException(fromBody, "OK error", status, null, ex);
        }
        return new SocialApiException(ErrorClass.PERMANENT, "OK error: " + status, status, null, ex);
    }

    private static byte[] storyBytes(PostVariantContent content, boolean video) {
        List<MediaAssetRef> media = PostVariantMeta.media(content == null ? Map.of() : content.meta());
        if (!media.isEmpty() && media.getFirst().hasContentBytes()) {
            return media.getFirst().contentBytes();
        }
        throw new SocialApiException(
                ErrorClass.PERMANENT,
                "OK moment publish requires media bytes from S3 (MediaPublishSupport); missing "
                        + (video ? "video" : "photo")
                        + " payload");
    }

    private static String normalizeKind(String kind, String mime) {
        String k = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        if (k.contains("video") || k.contains("clip")) {
            return "video";
        }
        if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("video/")) {
            return "video";
        }
        return "photo";
    }

    private static String fingerprintOf(String idempotencyKey, PostVariantContent content) {
        String fromMeta = PostVariantMeta.fingerprint(content == null ? Map.of() : content.meta());
        if (fromMeta != null && !fromMeta.isBlank()) {
            return fromMeta;
        }
        return idempotencyKey;
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

    private static String remoteUrl(String gid, String topicId) {
        return "https://ok.ru/group/" + gid + "/topic/" + topicId;
    }

    private static String momentUrl(String gid, String momentId) {
        return "https://ok.ru/group/" + gid + "/moment/" + momentId;
    }

    private static String trimTrailingSlash(String url, String fallback) {
        if (url == null || url.isBlank()) {
            return fallback;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
