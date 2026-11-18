package ru.posts.social.ok;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
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
import ru.posts.social.api.Network;
import ru.posts.social.api.NetworkCapabilities;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.PublishResult;
import ru.posts.social.api.SocialAdapter;
import ru.posts.social.api.SocialApiException;
import ru.posts.social.api.TokenRefreshRequest;
import ru.posts.social.api.TokenSet;

/**
 * OK (Odnoklassniki) adapter via {@code mediatopic.post} ({@code /ok/fb.do} or {@code /fb.do}).
 */
public class OkSocialAdapter implements SocialAdapter {

    private static final long OK_MOMENT_TTL_MS = 86_400_000L;

    private final RestClient restClient;
    private final RestClient absoluteClient;
    private final ObjectMapper objectMapper;

    public OkSocialAdapter(OkAdapterProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
        return new NetworkCapabilities(
                Network.OK,
                true,
                false,
                false,
                Map.of("mediatopicPost", true, "guidIdempotency", true, "stories", true, "moments", true));
    }

    @Override
    public PublishResult publish(ChannelContext ctx, PostVariantContent content, String idempotencyKey) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("method", "mediatopic.post");
        form.add("gid", ctx.externalId());
        form.add("attachment", content == null || content.text() == null ? "" : content.text());
        form.add("idempotency_key", idempotencyKey);
        form.add("access_token", token(ctx));
        try {
            JsonNode body = restClient
                    .post()
                    .uri("/ok/fb.do")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            return parsePublish(body, ctx.externalId());
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            throw new SocialApiException(
                    ErrorClass.UNKNOWN_OUTCOME, "OK call interrupted: " + ex.getMessage(), null, null, ex);
        } catch (RestClientException ex) {
            throw new SocialApiException(
                    ErrorClass.RETRYABLE, "OK transport error: " + ex.getMessage(), null, null, ex);
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
            uploadForm.add("access_token", token(ctx));
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

            MultipartBodyBuilder multipart = new MultipartBodyBuilder();
            String filename = video ? "moment.mp4" : "moment.jpg";
            String mime = video ? "video/mp4" : "image/jpeg";
            byte[] stubBytes = video
                    ? new byte[] {0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70}
                    : new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
            multipart
                    .part("file", new ByteArrayResource(stubBytes) {
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
            commitForm.add("access_token", token(ctx));
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
        form.add("method", "moments.delete");
        form.add("moment_id", remoteStoryId);
        form.add("gid", ctx.externalId());
        form.add("access_token", token(ctx));
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

    private Optional<PublishResult> findInControl(ChannelContext ctx, String idempotencyKey, boolean story) {
        try {
            JsonNode posts = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/_control/posts")
                            .queryParam("network", "ok")
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
                                story ? momentUrl(ctx.externalId(), remoteId) : remoteUrl(ctx.externalId(), remoteId);
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
    public void delete(ChannelContext ctx, String remotePostId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("method", "mediatopic.delete");
        form.add("topic_id", remotePostId);
        form.add("gid", ctx.externalId());
        form.add("access_token", token(ctx));
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
    public Optional<TokenSet> refreshToken(TokenRefreshRequest request) {
        // OK token refresh is wired when channel OAuth is implemented (channel API layer).
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
            case 2 -> ErrorClass.AUTH;
            case 4 -> ErrorClass.RATE_LIMITED;
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
        ErrorClass fromBody = classifyOkBody(ex.getResponseBodyAsString());
        if (fromBody != null) {
            return new SocialApiException(fromBody, "OK error", status, null, ex);
        }
        return new SocialApiException(ErrorClass.PERMANENT, "OK error: " + status, status, null, ex);
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

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String trimTrailingSlash(String url, String fallback) {
        if (url == null || url.isBlank()) {
            return fallback;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
