package ru.posts.api.admin;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.persistence.entity.AdminAuditLogEntity;
import ru.posts.persistence.repository.AdminAuditLogRepository;

@Component
@Profile("!openapi")
public class AdminAuditRecorder {

    private final AdminAuditLogRepository adminAuditLogRepository;

    public AdminAuditRecorder(AdminAuditLogRepository adminAuditLogRepository) {
        this.adminAuditLogRepository = adminAuditLogRepository;
    }

    @Transactional
    public AdminAuditLogEntity record(
            UUID actorUserId,
            String action,
            String targetType,
            UUID targetId,
            String reason,
            Map<String, Object> details,
            String ip) {
        AdminAuditLogEntity entry = new AdminAuditLogEntity();
        entry.setActorUserId(actorUserId);
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setReason(reason);
        entry.setDetails(details == null || details.isEmpty() ? Map.of() : details);
        entry.setIp(normalizeIp(ip));
        entry.setCreatedAt(Instant.now());
        return adminAuditLogRepository.save(entry);
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
