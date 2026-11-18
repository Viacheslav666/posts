package ru.posts.emulator.vk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class VkCallbackServerStore {

    private final AtomicLong seq = new AtomicLong(1000);
    private final ConcurrentHashMap<String, List<Server>> byGroup = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> confirmationCodes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, String>> settings = new ConcurrentHashMap<>();

    public synchronized String confirmationCode(String groupId) {
        return confirmationCodes.computeIfAbsent(groupId, g -> "conf-" + g + "-" + seq.incrementAndGet());
    }

    public synchronized Server add(String groupId, String url, String title, String secret) {
        Server server = new Server(String.valueOf(seq.incrementAndGet()), url, title, secret, "ok");
        byGroup.computeIfAbsent(groupId, g -> new ArrayList<>()).add(server);
        return server;
    }

    public synchronized List<Server> list(String groupId) {
        return List.copyOf(byGroup.getOrDefault(groupId, List.of()));
    }

    public synchronized boolean delete(String groupId, String serverId) {
        List<Server> servers = byGroup.get(groupId);
        if (servers == null) {
            return false;
        }
        return servers.removeIf(s -> s.id().equals(serverId));
    }

    public synchronized void setSettings(String groupId, String serverId, Map<String, String> events) {
        settings.put(groupId + ":" + serverId, Map.copyOf(events));
    }

    public synchronized Server findByUrl(String groupId, String url) {
        return list(groupId).stream()
                .filter(s -> s.url().equals(url))
                .findFirst()
                .orElse(null);
    }

    public synchronized void reset() {
        byGroup.clear();
        confirmationCodes.clear();
        settings.clear();
    }

    public record Server(String id, String url, String title, String secret, String status) {}
}
