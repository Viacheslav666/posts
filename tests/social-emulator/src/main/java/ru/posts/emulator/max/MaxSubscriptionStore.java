package ru.posts.emulator.max;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class MaxSubscriptionStore {

    private final ConcurrentHashMap<String, List<Subscription>> byToken = new ConcurrentHashMap<>();

    public synchronized void put(String token, String url, String secret, List<String> updateTypes) {
        List<Subscription> list = byToken.computeIfAbsent(token, t -> new ArrayList<>());
        list.removeIf(s -> s.url().equals(url));
        list.add(new Subscription(url, secret, updateTypes == null ? List.of() : List.copyOf(updateTypes)));
    }

    public synchronized List<Subscription> list(String token) {
        return List.copyOf(byToken.getOrDefault(token, List.of()));
    }

    public synchronized void delete(String token, String url) {
        List<Subscription> list = byToken.get(token);
        if (list != null) {
            list.removeIf(s -> s.url().equals(url));
        }
    }

    public synchronized Subscription findByUrl(String url) {
        for (List<Subscription> list : byToken.values()) {
            for (Subscription s : list) {
                if (s.url().equals(url)) {
                    return s;
                }
            }
        }
        return null;
    }

    public synchronized void reset() {
        byToken.clear();
    }

    public record Subscription(String url, String secret, List<String> updateTypes) {}
}
