package ru.posts.security.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.posts.persistence.entity.SocialAccountEntity;
import ru.posts.persistence.entity.WorkspaceDataKeyEntity;
import ru.posts.persistence.repository.SocialAccountRepository;
import ru.posts.persistence.repository.WorkspaceDataKeyRepository;
import ru.posts.security.crypto.TokenCryptoService;

/**
 * Rotates VK ID user tokens ({@code grant_type=refresh_token}) and persists the new pair. A pessimistic row lock plus
 * an expiry re-check stops two workers from presenting the same refresh token.
 */
public class VkIdUserTokenRefresher {

    private static final Logger log = LoggerFactory.getLogger(VkIdUserTokenRefresher.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VkChannelConnectProperties properties;
    private final SocialAccountRepository accounts;
    private final WorkspaceDataKeyRepository keys;
    private final TokenCryptoService crypto;
    private final RestClient http;

    public VkIdUserTokenRefresher(
            VkChannelConnectProperties properties,
            SocialAccountRepository accounts,
            WorkspaceDataKeyRepository keys,
            TokenCryptoService crypto,
            RestClient http) {
        this.properties = properties;
        this.accounts = accounts;
        this.keys = keys;
        this.crypto = crypto;
        this.http = http;
    }

    @Transactional
    public SocialAccountEntity ensureFresh(UUID accountId) {
        if (accountId == null) {
            return null;
        }
        SocialAccountEntity account = accounts.findByIdForUpdate(accountId).orElse(null);
        if (account == null || !shouldRefresh(account)) {
            return account;
        }
        String refresh = unseal(account.getWorkspaceId(), account.getRefreshTokenEnc());
        if (refresh == null || refresh.isBlank()) {
            return account;
        }
        String state = randomState();
        JsonNode body = postRefresh(refresh, account.getDeviceId(), state);
        String echoed = text(body, "state");
        if (echoed == null || !echoed.equals(state)) {
            throw new IllegalStateException("OAuth state mismatch for vk");
        }
        String access = text(body, "access_token");
        if (access == null || access.isBlank()) {
            throw new IllegalStateException("OAuth token exchange failed for vk");
        }
        account.setAccessTokenEnc(seal(account.getWorkspaceId(), access));
        String rotated = text(body, "refresh_token");
        if (rotated != null && !rotated.isBlank()) {
            account.setRefreshTokenEnc(seal(account.getWorkspaceId(), rotated));
        }
        long expiresIn = body.path("expires_in").asLong(3600);
        account.setTokenExpiresAt(Instant.now().plusSeconds(expiresIn));
        account.setUpdatedAt(Instant.now());
        return accounts.save(account);
    }

    /**
     * Plaintext access token after a refresh when this account is a VK ID user token. Empty when the account is not
     * managed here (emulator or non-expiring tokens) so callers keep their existing unseal path.
     */
    @Transactional
    public Optional<String> refreshedAccessToken(UUID accountId) {
        if (accountId == null || !properties.vkIdMode()) {
            return Optional.empty();
        }
        SocialAccountEntity account = accounts.findById(accountId).orElse(null);
        if (account == null || !isVkIdUserToken(account)) {
            return Optional.empty();
        }
        SocialAccountEntity fresh = ensureFresh(accountId);
        if (fresh == null) {
            return Optional.empty();
        }
        String token = unseal(fresh.getWorkspaceId(), fresh.getAccessTokenEnc());
        return token == null || token.isBlank() ? Optional.empty() : Optional.of(token);
    }

    private boolean shouldRefresh(SocialAccountEntity account) {
        if (!isVkIdUserToken(account)) {
            return false;
        }
        Instant expiresAt = account.getTokenExpiresAt();
        if (expiresAt == null) {
            return false;
        }
        return !expiresAt.isAfter(Instant.now().plus(5, ChronoUnit.MINUTES));
    }

    private boolean isVkIdUserToken(SocialAccountEntity account) {
        return properties.vkIdMode()
                && "vk".equals(account.getNetwork())
                && account.getDeviceId() != null
                && !account.getDeviceId().isBlank()
                && account.getRefreshTokenEnc() != null
                && account.getRefreshTokenEnc().length > 0;
    }

    private JsonNode postRefresh(String refreshToken, String deviceId, String state) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("grant_type", "refresh_token");
        fields.put("refresh_token", refreshToken);
        fields.put("client_id", properties.vkClientId());
        fields.put("device_id", deviceId);
        fields.put("state", state);
        try {
            JsonNode body = http.post()
                    .uri(properties.vkTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form(fields))
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || body.hasNonNull("error") || body.has("error_code")) {
                log.warn("VK ID refresh failed: error={}", body == null ? "empty" : text(body, "error"));
                throw new IllegalStateException("OAuth token exchange failed for vk");
            }
            return body;
        } catch (RestClientResponseException ex) {
            log.warn("VK ID refresh HTTP {}", ex.getStatusCode().value());
            throw new IllegalStateException("OAuth token exchange failed for vk");
        }
    }

    private byte[] seal(UUID workspaceId, String plaintext) {
        return crypto.seal(dek(workspaceId), plaintext);
    }

    private String unseal(UUID workspaceId, byte[] blob) {
        if (blob == null || blob.length == 0 || (blob.length == 1 && blob[0] == 0)) {
            return null;
        }
        return crypto.unseal(dek(workspaceId), blob);
    }

    private byte[] dek(UUID workspaceId) {
        WorkspaceDataKeyEntity key =
                keys.findById(workspaceId).orElseThrow(() -> new IllegalStateException("Missing workspace DEK"));
        return crypto.unwrapDek(key.getDekEnc());
    }

    private static String form(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue() == null ? "" : e.getValue()));
        }
        return sb.toString();
    }

    private static String randomState() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.get(field).asText();
        return value.isBlank() ? null : value;
    }
}
