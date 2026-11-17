package ru.posts.api.workspace;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.persistence.entity.AuditLogEntity;
import ru.posts.persistence.repository.AuditLogRepository;

@Component
@Profile("!openapi")
public class AuditRecorder {

    private final AuditLogRepository auditLogRepository;

    public AuditRecorder(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(
            UUID workspaceId,
            UUID actorId,
            String action,
            String entity,
            UUID entityId,
            Map<String, Object> diff,
            String ip) {
        AuditLogEntity entry = new AuditLogEntity();
        entry.setWorkspaceId(workspaceId);
        entry.setActorId(actorId);
        entry.setAction(action);
        entry.setEntity(entity);
        entry.setEntityId(entityId);
        entry.setDiff(diff == null || diff.isEmpty() ? null : diff);
        entry.setIp(normalizeIp(ip));
        entry.setCreatedAt(Instant.now());
        auditLogRepository.save(entry);
    }

    private static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String cleaned = ip;
        int comma = cleaned.indexOf(',');
        if (comma >= 0) {
            cleaned = cleaned.substring(0, comma).trim();
        }
        if ("::1".equals(cleaned) || "0:0:0:0:0:0:0:1".equals(cleaned)) {
            return "127.0.0.1";
        }
        if (cleaned.startsWith("[") && cleaned.contains("]")) {
            cleaned = cleaned.substring(1, cleaned.indexOf(']'));
        }
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }
}
