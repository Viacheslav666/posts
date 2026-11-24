package ru.posts.api.channel;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** HTTP client for OAuth / permission checks against social APIs or the QA emulator. */
@Component
public class SocialConnectClient {

    private static final Logger log = LoggerFactory.getLogger(SocialConnectClient.class);

    private final RestClient socialClient;
    private final RestClient vkApiClient;
    private final ChannelProperties properties;

    public SocialConnectClient(ChannelProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.socialClient = RestClient.builder()
                .baseUrl(trimSlash(properties.socialBaseUrl()))
                .requestFactory(factory)
                .build();
        this.vkApiClient = RestClient.builder()
                .baseUrl(trimSlash(properties.vkApiBaseUrl()))
                .requestFactory(factory)
                .build();
    }

    public record OAuthToken(
            String accessToken, String refreshToken, Instant expiresAt, String externalUserId, String displayName) {}

    public record RemoteChannel(
            String externalId,
            String type,
            String title,
            String avatarUrl,
            String username,
            boolean admin,
            String groupToken) {}

    public record BotPermissionCheck(boolean ok, String reason, String title, String type, String username) {}

    public OAuthToken exchangeVk(String code, String codeVerifier, String redirectUri, String deviceId, String state) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("grant_type", "authorization_code");
        fields.put("code", code);
        fields.put("code_verifier", codeVerifier == null ? "" : codeVerifier);
        fields.put("redirect_uri", redirectUri);
        fields.put("client_id", properties.vkClientId());
        if (properties.vkIdMode()) {
            fields.put("device_id", deviceId == null ? "" : deviceId);
            fields.put("state", state == null ? "" : state);
        } else {
            fields.put("client_secret", properties.vkClientSecret());
        }
        JsonNode body = postForm("vk", resolveTokenUri(), fields);
        if (properties.vkIdMode()) {
            validateReturnedState(body, state);
        }
        OAuthToken token = parseOAuth(body, "vk");
        if (token.displayName() == null || token.displayName().startsWith("vk-user-")) {
            String name = fetchVkUserDisplayName(token.accessToken());
            if (name != null) {
                return new OAuthToken(
                        token.accessToken(), token.refreshToken(), token.expiresAt(), token.externalUserId(), name);
            }
        }
        return token;
    }

    public OAuthToken refreshVk(String refreshToken, String deviceId, String state) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("grant_type", "refresh_token");
        fields.put("refresh_token", refreshToken);
        fields.put("client_id", properties.vkClientId());
        if (properties.vkIdMode()) {
            if (state == null || state.isBlank()) {
                throw new IllegalArgumentException("state is required for VK ID refresh");
            }
            fields.put("device_id", deviceId == null ? "" : deviceId);
            fields.put("state", state);
        } else {
            fields.put("client_secret", properties.vkClientSecret());
        }
        JsonNode body = postForm("vk", resolveTokenUri(), fields);
        if (properties.vkIdMode()) {
            requireEchoedState(body, state);
        }
        return parseOAuth(body, "vk");
    }

    public List<RemoteChannel> listVkAdminTargets(String accessToken) {
        JsonNode body = properties.vkIdMode()
                ? vkApiForm("/method/groups.get", vkGroupsParams(accessToken))
                : socialClient
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/method/groups.get")
                                .queryParam("access_token", accessToken)
                                .queryParam("extended", "1")
                                .queryParam("filter", "admin")
                                .build())
                        .retrieve()
                        .body(JsonNode.class);
        List<RemoteChannel> channels = parseVkGroups(body);
        if (properties.vkIdMode() && channels.stream().noneMatch(c -> "profile".equals(c.type()))) {
            String profileName = fetchVkUserDisplayName(accessToken);
            if (profileName != null) {
                String userId = fetchVkUserId(accessToken);
                if (userId != null) {
                    channels.add(new RemoteChannel(userId, "profile", profileName, null, null, true, null));
                }
            }
        }
        return channels;
    }

    public OAuthToken exchangeOk(String code, String redirectUri) {
        JsonNode body = postForm(
                "ok",
                properties.okTokenUri(),
                Map.of(
                        "grant_type",
                        "authorization_code",
                        "code",
                        code,
                        "redirect_uri",
                        redirectUri,
                        "client_id",
                        properties.okClientId(),
                        "client_secret",
                        properties.okClientSecret()));
        return parseOAuth(body, "ok");
    }

    public List<RemoteChannel> listOkAdminGroups(String accessToken) {
        JsonNode body = socialClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/ok/fb.do")
                        .queryParam("method", "group.getUserGroupsByIds")
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .body(JsonNode.class);
        return parseOkGroups(body);
    }

    public BotPermissionCheck checkTelegram(String botToken, String usernameOrChatId) {
        try {
            JsonNode body = socialClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/bot{token}/getChat")
                            .queryParam("chat_id", usernameOrChatId)
                            .build(botToken))
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || !body.path("ok").asBoolean(false)) {
                return new BotPermissionCheck(false, "chat_not_found", null, null, null);
            }
            JsonNode chat = body.path("result");
            String chatId = chat.path("id").asText(null);
            JsonNode member = socialClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/bot{token}/getChatMember")
                            .queryParam("chat_id", chatId)
                            .build(botToken))
                    .retrieve()
                    .body(JsonNode.class);
            boolean canPost = member != null
                    && member.path("ok").asBoolean(false)
                    && member.path("result").path("can_post_messages").asBoolean(false);
            if (!canPost) {
                String status = member == null
                        ? "unknown"
                        : member.path("result").path("status").asText("unknown");
                return new BotPermissionCheck(
                        false,
                        "Bot lacks can_post_messages (status=" + status
                                + "). Add the bot as channel admin with post rights.",
                        text(chat, "title"),
                        text(chat, "type"),
                        text(chat, "username"));
            }
            return new BotPermissionCheck(
                    true, null, text(chat, "title"), mapTgType(text(chat, "type")), text(chat, "username"));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 404) {
                return new BotPermissionCheck(false, "chat_not_found", null, null, null);
            }
            throw ex;
        }
    }

    public BotPermissionCheck checkMax(String botToken, String usernameOrChatId) {
        try {
            JsonNode body = socialClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/max/chats/{chatId}")
                            .queryParam("access_token", botToken)
                            .build(usernameOrChatId.startsWith("@") ? usernameOrChatId.substring(1) : usernameOrChatId))
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || (body.has("error") && !body.path("error").isMissingNode())) {
                return new BotPermissionCheck(false, "chat_not_found", null, null, null);
            }
            boolean canWrite = body.path("can_write").asBoolean(false)
                    || body.path("permissions").path("write").asBoolean(false);
            if (!canWrite) {
                return new BotPermissionCheck(
                        false,
                        "Max bot lacks write permissions. Grant admin/publish rights in the channel.",
                        text(body, "title"),
                        text(body, "type"),
                        text(body, "username"));
            }
            return new BotPermissionCheck(
                    true, null, text(body, "title"), mapMaxType(text(body, "type")), text(body, "username"));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 403) {
                return new BotPermissionCheck(
                        false,
                        "Max bot lacks write permissions. Grant admin/publish rights in the channel.",
                        null,
                        null,
                        null);
            }
            if (ex.getStatusCode().value() == 404) {
                return new BotPermissionCheck(false, "chat_not_found", null, null, null);
            }
            throw ex;
        }
    }

    public boolean probeToken(String network, String accessToken, String externalId) {
        try {
            return switch (network) {
                case "vk" -> {
                    boolean profile = isVkProfile(externalId);
                    JsonNode body = properties.vkIdMode()
                            ? vkApiForm(
                                    profile ? "/method/users.get" : "/method/groups.getById",
                                    profile
                                            ? Map.of(
                                                    "access_token",
                                                    accessToken,
                                                    "v",
                                                    properties.vkApiVersion(),
                                                    "user_ids",
                                                    externalId)
                                            : Map.of(
                                                    "access_token",
                                                    accessToken,
                                                    "v",
                                                    properties.vkApiVersion(),
                                                    "group_id",
                                                    externalId.replace("-", "")))
                            : socialClient
                                    .get()
                                    .uri(uriBuilder -> {
                                        if (profile) {
                                            return uriBuilder
                                                    .path("/method/users.get")
                                                    .queryParam("access_token", accessToken)
                                                    .queryParam("user_ids", externalId)
                                                    .build();
                                        }
                                        return uriBuilder
                                                .path("/method/groups.getById")
                                                .queryParam("access_token", accessToken)
                                                .queryParam("group_id", externalId.replace("-", ""))
                                                .build();
                                    })
                                    .retrieve()
                                    .body(JsonNode.class);
                    yield body != null && !body.has("error");
                }
                case "tg" -> checkTelegram(accessToken, externalId).ok();
                case "ok" -> {
                    JsonNode body = socialClient
                            .get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/ok/fb.do")
                                    .queryParam("method", "group.getInfo")
                                    .queryParam("access_token", accessToken)
                                    .queryParam("gids", externalId)
                                    .build())
                            .retrieve()
                            .body(JsonNode.class);
                    yield body != null && !body.has("error_code");
                }
                case "max" -> checkMax(accessToken, externalId).ok();
                default -> false;
            };
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public String buildVkAuthorizeUrl(String state, String codeChallenge, String redirectUri) {
        return properties.vkAuthorizationUri()
                + "?response_type=code"
                + "&client_id="
                + enc(properties.vkClientId())
                + "&redirect_uri="
                + enc(redirectUri)
                + "&state="
                + enc(state)
                + "&code_challenge="
                + enc(codeChallenge)
                + "&code_challenge_method=S256"
                + "&scope="
                + enc(properties.vkScopes());
    }

    public String buildOkAuthorizeUrl(String state, String redirectUri) {
        return properties.okAuthorizationUri()
                + "?response_type=code"
                + "&client_id="
                + enc(properties.okClientId())
                + "&redirect_uri="
                + enc(redirectUri)
                + "&state="
                + enc(state)
                + "&scope="
                + enc("VALUABLE_ACCESS;LONG_ACCESS_TOKEN;GROUP_CONTENT");
    }

    private Map<String, String> vkGroupsParams(String accessToken) {
        return Map.of(
                "access_token",
                accessToken,
                "v",
                properties.vkApiVersion(),
                "extended",
                "1",
                "filter",
                properties.vkIdMode() ? "admin,editor" : "admin");
    }

    private JsonNode vkApiForm(String path, Map<String, String> fields) {
        return vkApiClient
                .post()
                .uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form(fields))
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode postForm(String provider, String uri, Map<String, String> fields) {
        try {
            JsonNode body = socialClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form(fields))
                    .retrieve()
                    .body(JsonNode.class);
            if (body != null && (body.has("error") || body.has("error_code"))) {
                log.warn(
                        "OAuth {} request failed: error={} description={}",
                        provider,
                        text(body, "error"),
                        text(body, "error_description"));
                throw new IllegalStateException("OAuth token exchange failed for " + provider);
            }
            return body;
        } catch (RestClientResponseException ex) {
            log.warn("OAuth {} HTTP {} {}", provider, ex.getStatusCode().value(), ex.getMessage());
            throw new IllegalStateException("OAuth token exchange failed for " + provider);
        }
    }

    private static boolean isVkProfile(String externalId) {
        return externalId != null && !externalId.isBlank() && !externalId.startsWith("-");
    }

    private String resolveTokenUri() {
        String uri = properties.vkTokenUri();
        if (uri.startsWith("http")) {
            return uri;
        }
        return trimSlash(properties.socialBaseUrl()) + uri;
    }

    private void validateReturnedState(JsonNode body, String expectedState) {
        if (body == null || expectedState == null || expectedState.isBlank()) {
            return;
        }
        String returned = text(body, "state");
        if (returned != null && !returned.equals(expectedState)) {
            throw new IllegalStateException("OAuth state mismatch for vk");
        }
    }

    private void requireEchoedState(JsonNode body, String expectedState) {
        String returned = body == null ? null : text(body, "state");
        if (returned == null || !returned.equals(expectedState)) {
            throw new IllegalStateException("OAuth state mismatch for vk");
        }
    }

    private String fetchVkUserDisplayName(String accessToken) {
        try {
            JsonNode body = vkApiForm(
                    "/method/users.get",
                    Map.of("access_token", accessToken, "v", properties.vkApiVersion(), "fields", "photo_100"));
            if (body == null || body.has("error")) {
                return null;
            }
            JsonNode user =
                    body.path("response").isArray() && body.path("response").size() > 0
                            ? body.path("response").get(0)
                            : null;
            if (user == null) {
                return null;
            }
            String first = text(user, "first_name");
            String last = text(user, "last_name");
            if (first == null && last == null) {
                return null;
            }
            if (first == null) {
                return last;
            }
            if (last == null) {
                return first;
            }
            return first + " " + last;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String fetchVkUserId(String accessToken) {
        try {
            JsonNode body =
                    vkApiForm("/method/users.get", Map.of("access_token", accessToken, "v", properties.vkApiVersion()));
            if (body == null || body.has("error") || !body.path("response").isArray()) {
                return null;
            }
            JsonNode user =
                    body.path("response").size() > 0 ? body.path("response").get(0) : null;
            return user == null ? null : user.path("id").asText(null);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private OAuthToken parseOAuth(JsonNode body, String network) {
        if (body == null || body.has("error") || body.has("error_code")) {
            throw new IllegalStateException("OAuth token exchange failed for " + network);
        }
        String access = text(body, "access_token");
        String refresh = text(body, "refresh_token");
        long expiresIn = body.path("expires_in").asLong(3600);
        String userId = firstText(body, "user_id", "uid", "x_mail_ru_vid");
        if (userId == null) {
            userId = firstText(body.path("user"), "user_id", "id");
        }
        String name = firstText(body, "name", "display_name");
        if (name == null) {
            name = network + "-user-" + userId;
        }
        return new OAuthToken(access, refresh, Instant.now().plusSeconds(expiresIn), userId, name);
    }

    private List<RemoteChannel> parseVkGroups(JsonNode body) {
        List<RemoteChannel> result = new ArrayList<>();
        if (body == null || body.has("error")) {
            return result;
        }
        JsonNode items = body.path("response").path("items");
        if (!items.isArray()) {
            return result;
        }
        for (JsonNode item : items) {
            boolean admin = item.path("is_admin").asInt(0) == 1
                    || item.path("admin_level").asInt(0) > 0;
            String id = item.path("id").asText();
            String avatar = firstText(item, "photo_100", "photo_50");
            result.add(new RemoteChannel(
                    "-" + id,
                    "group",
                    text(item, "name"),
                    avatar,
                    text(item, "screen_name"),
                    admin,
                    text(item, "access_token")));
        }
        JsonNode profile = body.path("response").path("profile");
        if (!profile.isMissingNode() && !profile.isNull()) {
            result.add(new RemoteChannel(
                    profile.path("id").asText(),
                    "profile",
                    text(profile, "first_name") + " " + text(profile, "last_name"),
                    firstText(profile, "photo_100", "photo_50"),
                    null,
                    true,
                    null));
        }
        return result;
    }

    private List<RemoteChannel> parseOkGroups(JsonNode body) {
        List<RemoteChannel> result = new ArrayList<>();
        if (body == null) {
            return result;
        }
        JsonNode groups = body.isArray() ? body : body.path("groups");
        if (!groups.isArray()) {
            return result;
        }
        for (JsonNode item : groups) {
            boolean admin = item.path("role").asText("").equalsIgnoreCase("ADMIN")
                    || item.path("is_admin").asBoolean(false);
            result.add(new RemoteChannel(
                    item.path("uid").asText(item.path("id").asText()),
                    "group",
                    text(item, "name"),
                    text(item, "pic_avatar"),
                    null,
                    admin,
                    null));
        }
        return result;
    }

    private static String mapTgType(String type) {
        if (type == null) {
            return "channel";
        }
        return switch (type) {
            case "group", "supergroup" -> "chat";
            case "channel" -> "channel";
            default -> "channel";
        };
    }

    private static String mapMaxType(String type) {
        if (type == null) {
            return "channel";
        }
        return type.equalsIgnoreCase("channel") ? "channel" : "chat";
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

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://127.0.0.1:8090";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
