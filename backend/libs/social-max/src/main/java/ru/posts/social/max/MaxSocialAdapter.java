package ru.posts.social.max;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.LinkedHashMap;
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
import ru.posts.social.api.Network;
import ru.posts.social.api.NetworkCapabilities;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.PublishResult;
import ru.posts.social.api.SocialAdapter;
import ru.posts.social.api.SocialApiException;

/** Max Bot API adapter ({@code POST /max/messages} or {@code /messages}). */
public class MaxSocialAdapter implements SocialAdapter {

    private final RestClient restClient;

    public MaxSocialAdapter(MaxAdapterProperties properties) {
        String baseUrl = trimTrailingSlash(properties.baseUrl(), "https://platform-api.max.ru");
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
        return new NetworkCapabilities(Network.MAX, true, false, false, Map.of("messages", true, "bearerAuth", true));
    }

    @Override
    public PublishResult publish(ChannelContext ctx, PostVariantContent content, String idempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", ctx.externalId());
        payload.put("text", content == null || content.text() == null ? "" : content.text());
        payload.put("idempotency_key", idempotencyKey);
        payload.put("access_token", token(ctx));
        try {
            JsonNode body = restClient
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/max/messages")
                            .queryParam("access_token", token(ctx))
                            .build())
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
    public void delete(ChannelContext ctx, String remotePostId) {
        try {
            restClient
                    .delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/max/messages/{messageId}")
                            .queryParam("access_token", token(ctx))
                            .build(remotePostId))
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

    private static String remoteUrl(String chatId, String messageId) {
        return "https://max.ru/c/" + chatId + "/" + messageId;
    }

    private static String trimTrailingSlash(String url, String fallback) {
        if (url == null || url.isBlank()) {
            return fallback;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
