package ru.posts.api.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import ru.posts.api.auth.AuthProperties;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;

public class RestOAuthProviderClient implements OAuthProviderClient {

    private final AuthProperties properties;
    private final RestClient restClient;

    public RestOAuthProviderClient(AuthProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public OAuthUserProfile exchange(String provider, String code, String codeVerifier, String redirectUri) {
        AuthProperties.OAuthProviderProperties cfg = properties.getOauth().get(provider);
        if (cfg == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Unknown OAuth provider");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", cfg.getClientId());
        form.add("client_secret", cfg.getClientSecret());
        form.add("code_verifier", codeVerifier);

        JsonNode tokenResponse;
        try {
            tokenResponse = restClient
                    .post()
                    .uri(cfg.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "OAuth token exchange failed");
        }
        if (tokenResponse == null || !tokenResponse.hasNonNull("access_token")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "OAuth token missing");
        }
        String accessToken = tokenResponse.get("access_token").asText();

        JsonNode userInfo;
        try {
            userInfo = restClient
                    .get()
                    .uri(cfg.getUserInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "OAuth userinfo failed");
        }
        if (userInfo == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "OAuth userinfo empty");
        }
        return mapProfile(provider, userInfo);
    }

    private static OAuthUserProfile mapProfile(String provider, JsonNode userInfo) {
        Map<String, Object> raw = new LinkedHashMap<>();
        userInfo.fields()
                .forEachRemaining(e -> raw.put(
                        e.getKey(),
                        e.getValue().isValueNode()
                                ? e.getValue().asText()
                                : e.getValue().toString()));

        if ("vk".equals(provider)) {
            String id = text(userInfo, "user_id", "id", "sub");
            String email = text(userInfo, "email");
            String name = firstNonBlank(
                    text(userInfo, "name"), join(text(userInfo, "first_name"), text(userInfo, "last_name")));
            return new OAuthUserProfile(
                    provider, id, email, email != null && !email.isBlank(), name == null ? "VK User" : name, raw);
        }
        if ("yandex".equals(provider)) {
            String id = text(userInfo, "id", "sub");
            String email = firstNonBlank(text(userInfo, "default_email"), text(userInfo, "email"));
            String name = firstNonBlank(
                    text(userInfo, "real_name"),
                    text(userInfo, "display_name"),
                    text(userInfo, "login"),
                    "Yandex User");
            return new OAuthUserProfile(provider, id, email, email != null && !email.isBlank(), name, raw);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Unsupported OAuth provider");
    }

    private static String text(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.hasNonNull(field)) {
                return node.get(field).asText();
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String join(String a, String b) {
        if (a == null && b == null) {
            return null;
        }
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a + " " + b;
    }
}
