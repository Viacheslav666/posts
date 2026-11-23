package ru.posts.grabber.publish;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.posts.persistence.entity.NotificationEntity;
import ru.posts.persistence.repository.NotificationRepository;

/** Logs every grabber setting problem and stores one in-app notice per rule, setting and code. */
@Component
@Profile("!openapi")
public class GrabberSettingNotifier {

    private static final Logger log = LoggerFactory.getLogger(GrabberSettingNotifier.class);

    private final NotificationRepository notifications;

    public GrabberSettingNotifier(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public void notify(UUID userId, UUID workspaceId, UUID ruleId, String setting, String code, String message) {
        log.warn("grabber setting rule={} setting={} code={} {}", ruleId, setting, code, message);
        if (userId == null || ruleId == null || setting == null || code == null) {
            return;
        }
        String dedupeKey = "grabber-setting:" + ruleId + ":" + setting + ":" + code;
        if (notifications.findByUserIdAndDedupeKey(userId, dedupeKey).isPresent()) {
            return;
        }
        NotificationEntity entity = new NotificationEntity();
        entity.setUserId(userId);
        entity.setWorkspaceId(workspaceId);
        entity.setType("grabber.setting_error");
        entity.setDedupeKey(dedupeKey);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", message == null ? "" : message);
        payload.put("setting", setting);
        payload.put("code", code);
        payload.put("ruleId", ruleId.toString());
        entity.setPayload(payload);
        entity.setCreatedAt(Instant.now());
        notifications.save(entity);
    }
}
