package ru.posts.emulator.vk;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * QA-11: tracks revoked VK community story tokens. When a group id is revoked, stories.* methods
 * return VK auth error (code 5) so publishers can enter {@code waiting_for_channel}.
 */
@Component
public class StoryTokenStore {

    private final Set<String> revokedGroupIds = ConcurrentHashMap.newKeySet();

    public void revoke(String groupId) {
        if (groupId != null && !groupId.isBlank()) {
            revokedGroupIds.add(normalize(groupId));
        }
    }

    public void restore(String groupId) {
        if (groupId != null && !groupId.isBlank()) {
            revokedGroupIds.remove(normalize(groupId));
        }
    }

    public boolean isRevoked(String groupId) {
        return groupId != null && revokedGroupIds.contains(normalize(groupId));
    }

    public void reset() {
        revokedGroupIds.clear();
    }

    public Set<String> snapshot() {
        return Set.copyOf(revokedGroupIds);
    }

    private static String normalize(String groupId) {
        String trimmed = groupId.trim();
        if (trimmed.startsWith("-")) {
            return trimmed.substring(1);
        }
        return trimmed;
    }
}
