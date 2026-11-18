package ru.posts.social.inbound.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** VK Callback API helpers (groups.*Callback*) using community token. */
public class VkCallbackClient {

    private final RestClient restClient;
    private final String apiVersion;

    public VkCallbackClient(String baseUrl, String apiVersion) {
        this.apiVersion = apiVersion == null || apiVersion.isBlank() ? "5.199" : apiVersion;
        this.restClient = RestClient.builder()
                .baseUrl(trim(baseUrl, "https://api.vk.com"))
                .build();
    }

    public List<CallbackServer> getCallbackServers(String groupId, String accessToken) {
        JsonNode body = call("groups.getCallbackServers", Map.of("group_id", absoluteGroupId(groupId)), accessToken);
        List<CallbackServer> servers = new ArrayList<>();
        JsonNode items = body.path("response").path("items");
        if (items.isArray()) {
            for (JsonNode item : items) {
                servers.add(new CallbackServer(
                        item.path("id").asText(),
                        item.path("url").asText(),
                        item.path("title").asText(),
                        item.path("status").asText("")));
            }
        }
        return servers;
    }

    public String getCallbackConfirmationCode(String groupId, String accessToken) {
        JsonNode body =
                call("groups.getCallbackConfirmationCode", Map.of("group_id", absoluteGroupId(groupId)), accessToken);
        return body.path("response").path("code").asText(null);
    }

    public String addCallbackServer(String groupId, String url, String title, String secretKey, String accessToken) {
        JsonNode body = call(
                "groups.addCallbackServer",
                Map.of("group_id", absoluteGroupId(groupId), "url", url, "title", title, "secret_key", secretKey),
                accessToken);
        return body.path("response").path("server_id").asText(null);
    }

    public void setCallbackSettings(String groupId, String serverId, Map<String, String> events, String accessToken) {
        MultiValueMap<String, String> form = baseForm(accessToken);
        form.add("group_id", absoluteGroupId(groupId));
        form.add("server_id", serverId);
        form.add("api_version", apiVersion);
        events.forEach(form::add);
        restClient
                .post()
                .uri("/method/groups.setCallbackSettings")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
    }

    public void deleteCallbackServer(String groupId, String serverId, String accessToken) {
        call(
                "groups.deleteCallbackServer",
                Map.of("group_id", absoluteGroupId(groupId), "server_id", serverId),
                accessToken);
    }

    private JsonNode call(String method, Map<String, String> params, String accessToken) {
        MultiValueMap<String, String> form = baseForm(accessToken);
        params.forEach(form::add);
        JsonNode body = restClient
                .post()
                .uri("/method/" + method)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        if (body != null && body.has("error")) {
            throw new IllegalStateException("VK " + method + " error: " + body.get("error"));
        }
        return body;
    }

    private MultiValueMap<String, String> baseForm(String accessToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("access_token", accessToken);
        form.add("v", apiVersion);
        return form;
    }

    private static String absoluteGroupId(String groupId) {
        if (groupId == null) {
            return "";
        }
        String g = groupId.startsWith("-") ? groupId.substring(1) : groupId;
        return g;
    }

    private static String trim(String baseUrl, String fallback) {
        String v = baseUrl == null || baseUrl.isBlank() ? fallback : baseUrl;
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }

    public record CallbackServer(String id, String url, String title, String status) {}
}
