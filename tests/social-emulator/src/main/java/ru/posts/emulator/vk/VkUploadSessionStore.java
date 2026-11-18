package ru.posts.emulator.vk;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * In-memory VK upload sessions (wall photo + stories). Cleared on {@code /_control/reset} so IT
 * isolation does not leak group_id across tests.
 */
@Component
public class VkUploadSessionStore {

    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();

    public Session create(String groupId) {
        String uploadId = UUID.randomUUID().toString();
        Session session = new Session(uploadId, groupId);
        sessions.put(uploadId, session);
        return session;
    }

    public Session get(String uploadId) {
        return sessions.get(uploadId);
    }

    public Session findByGuid(String guid) {
        if (guid == null || guid.isBlank()) {
            return null;
        }
        for (Session session : sessions.values()) {
            if (guid.equals(session.guid)) {
                return session;
            }
        }
        return null;
    }

    public Collection<Session> values() {
        return sessions.values();
    }

    public void reset() {
        sessions.clear();
    }

    public static final class Session {
        final String uploadId;
        final String groupId;
        boolean uploaded;
        String server;
        String photo;
        String hash;
        String linkText;
        String linkUrl;
        String guid;
        boolean video;

        Session(String uploadId, String groupId) {
            this.uploadId = uploadId;
            this.groupId = groupId;
        }

        public String uploadId() {
            return uploadId;
        }
    }
}
