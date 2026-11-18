package ru.posts.emulator.connect;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory OAuth codes and chat fixtures for BE-7 channel connect integration tests. */
@Component
public class ConnectFixtureStore {

    public record ChatFixture(
            String id, String type, String title, String username, boolean botIsAdmin, boolean canPost) {}

    private final ConcurrentHashMap<String, Map<String, Object>> oauthCodes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ChatFixture> telegramChats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ChatFixture> maxChats = new ConcurrentHashMap<>();

    public ConnectFixtureStore() {
        seed();
    }

    public void seed() {
        oauthCodes.clear();
        telegramChats.clear();
        maxChats.clear();

        oauthCodes.put(
                "vk-admin",
                Map.of(
                        "network", "vk",
                        "access_token", "vk-access-admin",
                        "refresh_token", "vk-refresh-admin",
                        "user_id", "1001",
                        "name", "VK Admin",
                        "groups",
                                List.of(Map.of(
                                        "id",
                                        2001,
                                        "name",
                                        "Brand News",
                                        "screen_name",
                                        "brand_news",
                                        "photo_50",
                                        "https://example.test/vk.png",
                                        "is_admin",
                                        1,
                                        "admin_level",
                                        3))));
        oauthCodes.put(
                "vk-no-admin",
                Map.of(
                        "network",
                        "vk",
                        "access_token",
                        "vk-access-noadmin",
                        "refresh_token",
                        "vk-refresh-noadmin",
                        "user_id",
                        "1002",
                        "name",
                        "VK Viewer",
                        "groups",
                        List.of()));

        oauthCodes.put(
                "ok-admin",
                Map.of(
                        "network",
                        "ok",
                        "access_token",
                        "ok-access-admin",
                        "refresh_token",
                        "ok-refresh-admin",
                        "user_id",
                        "ok-u1",
                        "name",
                        "OK Admin",
                        "groups",
                        List.of(Map.of("uid", "ok-g1", "name", "OK Group", "role", "ADMIN", "is_admin", true))));
        oauthCodes.put(
                "ok-no-admin",
                Map.of(
                        "network",
                        "ok",
                        "access_token",
                        "ok-access-noadmin",
                        "user_id",
                        "ok-u2",
                        "name",
                        "OK User",
                        "groups",
                        List.of(Map.of("uid", "ok-g2", "name", "OK Locked", "role", "USER", "is_admin", false))));

        putTg(new ChatFixture("-1002001", "channel", "TG Brand", "tg_brand", true, true));
        putTg(new ChatFixture("-1002002", "channel", "TG NoPost", "tg_nopost", true, false));
        putTg(new ChatFixture("-1002003", "channel", "TG Missing", "tg_missing", false, false));

        putMax(new ChatFixture("max-1", "channel", "Max Brand", "max_brand", true, true));
        putMax(new ChatFixture("max-2", "channel", "Max Locked", "max_locked", false, false));
    }

    public Map<String, Object> consumeOAuthCode(String code) {
        return oauthCodes.get(code);
    }

    public ChatFixture findTelegram(String ref) {
        if (ref == null) {
            return null;
        }
        String key = ref.startsWith("@") ? ref.substring(1) : ref;
        ChatFixture byUser = telegramChats.get("u:" + key.toLowerCase());
        if (byUser != null) {
            return byUser;
        }
        return telegramChats.get("id:" + key);
    }

    public ChatFixture findMax(String ref) {
        if (ref == null) {
            return null;
        }
        String key = ref.startsWith("@") ? ref.substring(1) : ref;
        ChatFixture byUser = maxChats.get("u:" + key.toLowerCase());
        if (byUser != null) {
            return byUser;
        }
        return maxChats.get("id:" + key);
    }

    private void putTg(ChatFixture fixture) {
        telegramChats.put("id:" + fixture.id(), fixture);
        if (fixture.username() != null) {
            telegramChats.put("u:" + fixture.username().toLowerCase(), fixture);
        }
    }

    private void putMax(ChatFixture fixture) {
        maxChats.put("id:" + fixture.id(), fixture);
        if (fixture.username() != null) {
            maxChats.put("u:" + fixture.username().toLowerCase(), fixture);
        }
    }
}
