package ru.posts.grabber.notify;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.persistence.entity.AutomationRuleEntity;
import ru.posts.persistence.entity.OutboxEntity;
import ru.posts.persistence.repository.OutboxRepository;

/**
 * Logs a grabber setting failure and, when {@code reliability.notify_on_error} is on, writes an
 * outbox event {@code grabber.setting_error} for the notification pipeline.
 */
@Component
@Profile("!openapi")
public class GrabberSettingReporter {

    private static final Logger log = LoggerFactory.getLogger(GrabberSettingReporter.class);

    private final OutboxRepository outboxRepository;

    public GrabberSettingReporter(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    public void report(AutomationRuleEntity rule, String setting, String reason, String detail) {
        String safeSetting = setting == null ? "grabber" : setting;
        String safeReason = reason == null ? "error" : reason;
        String safeDetail = detail == null ? "" : detail;
        log.warn(
                "Grabber setting rejected ruleId={} setting={} reason={} detail={}",
                rule.getId(),
                safeSetting,
                safeReason,
                safeDetail);
        if (!GrabberConfigSupport.notifyOnError(rule.getConfig())) {
            return;
        }
        Instant now = Instant.now();
        OutboxEntity outbox = new OutboxEntity();
        outbox.setAggregate("grabber_rule");
        outbox.setAggregateId(rule.getId());
        outbox.setEventType("grabber.setting_error");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspaceId", rule.getWorkspaceId().toString());
        payload.put("ruleId", rule.getId().toString());
        payload.put("ruleName", rule.getName() == null ? "" : rule.getName());
        payload.put("setting", safeSetting);
        payload.put("reason", safeReason);
        payload.put("detail", safeDetail);
        payload.put("title", "Ошибка настройки граббера");
        payload.put("body", safeDetail.isBlank() ? safeReason : safeDetail);
        outbox.setPayload(payload);
        outbox.setCreatedAt(now);
        outboxRepository.save(outbox);
    }

    /** Machine setting id for a skip reason produced by this feature. */
    public static String settingForReason(String reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case "max_age_days" -> "filters.max_age_days";
            case "rate_limit_per_day" -> "limits.rate_limit_per_day";
            case "rate_limit_per_source_per_day" -> "limits.rate_limit_per_source_per_day";
            case "postponed_horizon", "repost_object_missing" -> "target_mode";
            default -> null;
        };
    }

    public static String detailForReason(String reason, int count) {
        String base =
                switch (reason) {
                    case "max_age_days" -> "Пост старше заданного числа дней и не скопирован";
                    case "rate_limit_per_day" -> "Достигнут суточный лимит по всем источникам";
                    case "rate_limit_per_source_per_day" -> "Достигнут суточный лимит источника";
                    case "postponed_horizon" -> "Не удалось поставить запись в отложенные VK: дата позже чем через 7 дней";
                    case "repost_object_missing" -> "Репост невозможен: у поста источника нет числового id стены VK";
                    default -> reason;
                };
        return count > 1 ? base + " (" + count + ")" : base;
    }
}
