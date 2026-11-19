package ru.posts.emulator.telegram;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class TelegramWebhookStore {

    private final ConcurrentHashMap<String, Webhook> byToken = new ConcurrentHashMap<>();

    public void set(String token, String url, String secret, List<String> allowedUpdates) {
        byToken.put(
                token,
                new Webhook(url, secret, allowedUpdates == null ? List.of() : List.copyOf(allowedUpdates), null, 0));
    }

    public Webhook get(String token) {
        return byToken.get(token);
    }

    public void delete(String token) {
        byToken.remove(token);
    }

    public void reset() {
        byToken.clear();
    }

    public record Webhook(
            String url,
            String secretToken,
            List<String> allowedUpdates,
            String lastErrorMessage,
            int pendingUpdateCount) {}
}
