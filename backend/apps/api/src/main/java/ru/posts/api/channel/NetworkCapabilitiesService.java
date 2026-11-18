package ru.posts.api.channel;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.network.NetworkDtos;

@Service
public class NetworkCapabilitiesService {

    private final NetworkDtos.NetworkCapabilitiesResponse cached;

    public NetworkCapabilitiesService() {
        this.cached = load();
    }

    public NetworkDtos.NetworkCapabilitiesResponse get() {
        return cached;
    }

    @SuppressWarnings("unchecked")
    private static NetworkDtos.NetworkCapabilitiesResponse load() {
        try (InputStream in = new ClassPathResource("network-capabilities.yaml").getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            String version = string(root.get("version"));
            String checkedAt = string(root.get("checked_at"));
            List<Map<String, Object>> networks = (List<Map<String, Object>>) root.getOrDefault("networks", List.of());
            List<NetworkDtos.NetworkCapability> mapped = new ArrayList<>();
            for (Map<String, Object> node : networks) {
                mapped.add(mapNetwork(node));
            }
            return new NetworkDtos.NetworkCapabilitiesResponse(version, checkedAt, mapped);
        } catch (Exception ex) {
            return new NetworkDtos.NetworkCapabilitiesResponse(
                    "0.0.0",
                    "unknown",
                    List.of(
                            stub(ApiEnums.Network.vk, "ВКонтакте"),
                            stub(ApiEnums.Network.tg, "Telegram"),
                            stub(ApiEnums.Network.ok, "Одноклассники"),
                            stub(ApiEnums.Network.max, "Max")));
        }
    }

    @SuppressWarnings("unchecked")
    private static NetworkDtos.NetworkCapability mapNetwork(Map<String, Object> node) {
        String rawId = string(node.get("id"));
        ApiEnums.Network id = "telegram".equals(rawId) ? ApiEnums.Network.tg : ApiEnums.Network.valueOf(rawId);
        Map<String, Object> text = map(node.get("text"));
        Map<String, Object> formatting = map(node.get("formatting"));
        Map<String, Object> media = map(node.get("media"));
        Map<String, Object> features = map(node.get("features"));
        Map<String, Object> rates = map(node.get("api_limits"));
        if (rates.isEmpty()) {
            rates = map(node.get("rate_limits"));
        }
        return new NetworkDtos.NetworkCapability(
                id,
                string(node.get("name")),
                string(node.get("phase")),
                stringList(node.get("channel_types")),
                stringList(node.get("content_types")),
                new NetworkDtos.TextLimits(
                        intValue(text.get("max_chars")),
                        firstInt(text.get("caption_max_chars"), media.get("caption_max_chars"))),
                new NetworkDtos.FormattingCapability(
                        string(valueOf(formatting.get("mode")), "plain"),
                        stringList(valueOf(formatting.get("features"))),
                        stringList(valueOf(formatting.get("html_tags")))),
                new NetworkDtos.MediaLimits(
                        intValue(media.get("max_attachments_per_post")),
                        flatten(map(media.get("photo"))),
                        flatten(map(media.get("video")))),
                new NetworkDtos.FeatureFlags(
                        bool(features.get("pin")),
                        bool(features.get("unpin")),
                        bool(features.get("delete_after_publish")),
                        bool(features.get("first_comment")),
                        bool(features.get("inline_buttons")),
                        bool(features.get("native_schedule")),
                        bool(features.get("stories")),
                        bool(features.get("repost")),
                        bool(features.get("clips"))),
                new NetworkDtos.RateLimits(
                        doubleValue(first(rates.get("global_rps"), rates.get("global_msg_rps"))),
                        doubleValue(
                                first(rates.get("per_token_rps"), rates.get("user_token_rps"), rates.get("token_rps"))),
                        doubleValue(first(rates.get("per_chat_rps"), rates.get("per_chat_msg_rps"))),
                        flatten(rates)));
    }

    private static NetworkDtos.NetworkCapability stub(ApiEnums.Network id, String name) {
        return new NetworkDtos.NetworkCapability(
                id,
                name,
                "mvp",
                List.of(),
                List.of("post"),
                new NetworkDtos.TextLimits(null, null),
                new NetworkDtos.FormattingCapability("plain", List.of(), List.of()),
                new NetworkDtos.MediaLimits(null, Map.of(), Map.of()),
                new NetworkDtos.FeatureFlags(false, false, true, false, false, false, false, false, false),
                new NetworkDtos.RateLimits(null, null, null, Map.of()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static Object valueOf(Object node) {
        if (node instanceof Map<?, ?> m && m.containsKey("value")) {
            return m.get("value");
        }
        return node;
    }

    private static boolean bool(Object node) {
        Object value = valueOf(node);
        return value instanceof Boolean b && b;
    }

    private static Integer intValue(Object node) {
        Object value = valueOf(node);
        if (value instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static Double doubleValue(Object node) {
        Object value = valueOf(node);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    private static Integer firstInt(Object... nodes) {
        for (Object node : nodes) {
            Integer value = intValue(node);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object first(Object... nodes) {
        for (Object node : nodes) {
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object node) {
        Object value = valueOf(node);
        List<String> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
        }
        return out;
    }

    private static Map<String, Object> flatten(Map<String, Object> node) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            Object value = valueOf(entry.getValue());
            map.put(entry.getKey(), value);
        }
        return map;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String string(Object value, String fallback) {
        String s = string(value);
        return s == null || s.isBlank() ? fallback : s;
    }
}
