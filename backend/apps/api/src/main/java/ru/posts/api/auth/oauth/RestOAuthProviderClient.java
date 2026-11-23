package ru.posts.api.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.posts.api.auth.AuthProperties;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;

public class RestOAuthProviderClient implements OAuthProviderClient {

    private static final Logger log = LoggerFactory.getLogger(RestOAuthProviderClient.class);

    private final AuthProperties properties;
    private final RestClient restClient;

    public RestOAuthProviderClient(AuthProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public OAuthUserProfile exchange(String provider, OAuthCodeExchange exchange) {
        AuthProperties.OAuthProviderProperties cfg = properties.getOauth().get(provider);
        if (cfg == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Unknown OAuth provider");
        }
        return switch (provider) {
            case "vk" -> exchangeVkId(cfg, exchange);
            case "yandex" -> exchangeYandex(cfg, exchange);
            default -> throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Unsupported OAuth provider");
        };
    }

    /**
     * VK ID OAuth 2.1: {@code POST /oauth2/auth} (form) with PKCE verifier and the {@code device_id} returned on the
     * redirect, then {@code POST /oauth2/user_info} (form) with {@code client_id} + {@code access_token}. The profile
     * is wrapped in {@code {"user": {...}}}; {@code email} is only present when the user granted the scope.
     */
    private OAuthUserProfile exchangeVkId(AuthProperties.OAuthProviderProperties cfg, OAuthCodeExchange exchange) {
        if (isBlank(exchange.deviceId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "device_id is required for VK ID");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", exchange.code());
        form.add("code_verifier", exchange.codeVerifier());
        form.add("client_id", cfg.getClientId());
        form.add("device_id", exchange.deviceId());
        form.add("redirect_uri", exchange.redirectUri());
        form.add("state", exchange.state());

        JsonNode token = postForm("vk", cfg.getTokenUri(), form, "OAuth token exchange failed");
        String accessToken = text(token, "access_token");
        if (isBlank(accessToken)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "OAuth token missing");
        }
        String returnedState = text(token, "state");
        if (returnedState != null && !returnedState.equals(exchange.state())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid OAuth state");
        }

        MultiValueMap<String, String> userInfoForm = new LinkedMultiValueMap<>();
        userInfoForm.add("client_id", cfg.getClientId());
        userInfoForm.add("access_token", accessToken);
        JsonNode userInfo = postForm("vk", cfg.getUserInfoUri(), userInfoForm, "OAuth userinfo failed");
        JsonNode user = userInfo.hasNonNull("user") ? userInfo.get("user") : userInfo;

        String id = firstNonBlank(text(user, "user_id", "id", "sub"), text(token, "user_id"));
        String email = firstNonBlank(text(user, "email"), text(token, "email"));
        String name =
                firstNonBlank(text(user, "name"), join(text(user, "first_name"), text(user, "last_name")), "VK User");
        return new OAuthUserProfile("vk", id, email, !isBlank(email), name, rawMap(user));
    }

    private OAuthUserProfile exchangeYandex(AuthProperties.OAuthProviderProperties cfg, OAuthCodeExchange exchange) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", exchange.code());
        form.add("redirect_uri", exchange.redirectUri());
        form.add("client_id", cfg.getClientId());
        form.add("client_secret", cfg.getClientSecret());
        form.add("code_verifier", exchange.codeVerifier());

        JsonNode token = postForm("yandex", cfg.getTokenUri(), form, "OAuth token exchange failed");
        String accessToken = text(token, "access_token");
        if (isBlank(accessToken)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "OAuth token missing");
        }

        JsonNode userInfo;
        try {
            userInfo = restClient
                    .get()
                    .uri(cfg.getUserInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            log.warn("OAuth yandex userinfo request failed: {}", ex.toString());
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "OAuth userinfo failed");
        }
        if (userInfo == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "OAuth userinfo empty");
        }
        String id = text(userInfo, "id", "sub");
        String email = firstNonBlank(text(userInfo, "default_email"), text(userInfo, "email"));
        String name = firstNonBlank(
                text(userInfo, "real_name"), text(userInfo, "display_name"), text(userInfo, "login"), "Yandex User");
        return new OAuthUserProfile("yandex", id, email, !isBlank(email), name, rawMap(userInfo));
    }

    /** Providers report OAuth errors either as non-2xx or as 200 with an {@code error} field; both fail here. */
    private JsonNode postForm(String provider, String uri, MultiValueMap<String, String> form, String failure) {
        JsonNode body;
        try {
            body = restClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            log.warn(
                    "OAuth {} request to {} failed: HTTP {} {}",
                    provider,
                    uri,
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString());
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, failure);
        } catch (Exception ex) {
            log.warn("OAuth {} request to {} failed: {}", provider, uri, ex.toString());
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, failure);
        }
        if (body == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, failure);
        }
        if (body.hasNonNull("error")) {
            log.warn(
                    "OAuth {} request to {} returned error={} description={}",
                    provider,
                    uri,
                    body.get("error").asText(),
                    text(body, "error_description"));
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, failure);
        }
        return body;
    }

    private static Map<String, Object> rawMap(JsonNode node) {
        Map<String, Object> raw = new LinkedHashMap<>();
        node.fields()
                .forEachRemaining(e -> raw.put(
                        e.getKey(),
                        e.getValue().isValueNode()
                                ? e.getValue().asText()
                                : e.getValue().toString()));
        return raw;
    }

    private static String text(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            if (node.hasNonNull(field)) {
                String value = node.get(field).asText();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String join(String a, String b) {
        if (isBlank(a)) {
            return isBlank(b) ? null : b;
        }
        if (isBlank(b)) {
            return a;
        }
        return a + " " + b;
    }
}
