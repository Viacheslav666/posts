package ru.posts.social.ok;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
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

    private final RestClient restClient;
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
    }

    @Override
    public Network network() {
        return Network.OK;
    }

    @Override
    public NetworkCapabilities capabilities() {
        return new NetworkCapabilities(
                Network.OK, true, false, false, Map.of("mediatopicPost", true, "guidIdempotency", true));
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
    public Optional<PublishResult> findExisting(ChannelContext ctx, String idempotencyKey, PostVariantContent content) {
        try {
            JsonNode posts = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/_control/posts")
                            .queryParam("network", "ok")
                            .queryParam("channelId", ctx.externalId())
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
                        return Optional.of(new PublishResult(remoteId, remoteUrl(ctx.externalId(), remoteId)));
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
        if (body == null) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "OK empty response");
        }
        if (body.has("error_code")) {
            int code = body.path("error_code").asInt();
            throw new SocialApiException(
                    classifyOkErrorCode(code), body.path("error_msg").asText("OK error " + code));
        }
        String id = body.path("id").asText(null);
        if (id == null) {
            throw new SocialApiException(ErrorClass.RETRYABLE, "OK missing id");
        }
        return new PublishResult(id, remoteUrl(gid, id));
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
