package ru.posts.grabber.target;

import java.util.List;
import java.util.Map;
import ru.posts.grabber.config.GrabberConfigSupport;

/**
 * Rejects grabber settings that the {@link GrabberModeMatrix} says the chosen target mode cannot
 * apply. Messages are Russian because they are shown in the product UI.
 */
public final class GrabberModeRestrictions {

    private GrabberModeRestrictions() {}

    public static void validate(Map<String, Object> config, String ruleType) {
        String modeWire = GrabberConfigSupport.targetMode(config);
        if (!GrabberTargetMode.isKnown(modeWire)) {
            throw new IllegalArgumentException("Неизвестный режим публикации: " + modeWire);
        }
        GrabberTargetMode mode = GrabberTargetMode.fromWire(modeWire);
        if (mode.repost() && ruleType != null && !"grabber".equalsIgnoreCase(ruleType)) {
            throw new IllegalArgumentException(
                    "Репост доступен только для граббера VK: у RSS и сайта нет объекта wall для wall.repost");
        }

        int interval = GrabberConfigSupport.minIntervalMinutes(config);
        if (interval < 0 || interval > GrabberModeMatrix.MAX_INTERVAL_MINUTES) {
            throw new IllegalArgumentException("Интервал между постами должен быть от 0 до 120 минут");
        }
        if (!GrabberModeMatrix.supports(mode, interval, GrabberModeMatrix.Feature.INTERVAL) && interval > 0) {
            throw new IllegalArgumentException(
                    "Интервал между постами недоступен в отложенных записях VK. Укажите 0 или выберите публикацию сразу на стену");
        }

        Map<String, Object> firstComment = GrabberConfigSupport.firstComment(config);
        boolean firstCommentEnabled = bool(firstComment.get("enabled"));
        if (firstCommentEnabled
                && !GrabberModeMatrix.supports(mode, interval, GrabberModeMatrix.Feature.FIRST_COMMENT)) {
            throw new IllegalArgumentException("Первый комментарий недоступен в отложенных записях VK");
        }
        if (hasEntries(firstComment.get("attachments"))
                && !GrabberModeMatrix.supports(mode, interval, GrabberModeMatrix.Feature.COMMENT_ATTACHMENTS)) {
            throw new IllegalArgumentException(commentAttachmentMessage(mode, interval));
        }

        if (actionButtonEnabled(config)
                && !GrabberModeMatrix.supports(mode, interval, GrabberModeMatrix.Feature.ACTION_BUTTON)) {
            throw new IllegalArgumentException("Кнопка действия недоступна в отложенных записях VK");
        }
        if (scheduledDeleteEnabled(config)
                && !GrabberModeMatrix.supports(mode, interval, GrabberModeMatrix.Feature.SCHEDULED_DELETE)) {
            throw new IllegalArgumentException("Отложенное удаление недоступно в отложенных записях VK");
        }

        int maxAge = GrabberConfigSupport.maxAgeDays(config);
        if (maxAge < 0 || maxAge > 3650) {
            throw new IllegalArgumentException(
                    "«Не копировать посты старше n дней»: укажите 0 (не учитывать) или число до 3650");
        }
        int perDay = GrabberConfigSupport.rateLimitPerDay(config);
        int perSource = GrabberConfigSupport.rateLimitPerSourcePerDay(config);
        if (perDay < 0 || perDay > 500) {
            throw new IllegalArgumentException("Суточный лимит по всем источникам: 0 — не ограничивать, максимум 500");
        }
        if (perSource < 0 || perSource > 500) {
            throw new IllegalArgumentException("Суточный лимит на источник: 0 — не ограничивать, максимум 500");
        }
    }

    private static String commentAttachmentMessage(GrabberTargetMode mode, int interval) {
        if (mode.postponed()) {
            return "Вложения к комментарию недоступны в отложенных записях VK";
        }
        if (interval > 0) {
            return "Вложения к комментарию не работают вместе с интервалом между постами";
        }
        return "Вложения к комментарию недоступны в выбранном режиме";
    }

    static boolean actionButtonEnabled(Map<String, Object> config) {
        if (payloadEnabled(config == null ? null : config.get("action_button"))) {
            return true;
        }
        Map<String, Object> transform = GrabberConfigSupport.transform(config);
        return payloadEnabled(transform.get("action_button"));
    }

    static boolean scheduledDeleteEnabled(Map<String, Object> config) {
        if (deletePayload(config == null ? null : config.get("scheduled_delete"))) {
            return true;
        }
        if (positive(config == null ? null : config.get("delete_after_minutes"))) {
            return true;
        }
        Map<String, Object> transform = GrabberConfigSupport.transform(config);
        return deletePayload(transform.get("scheduled_delete")) || positive(transform.get("delete_after_minutes"));
    }

    private static boolean payloadEnabled(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return false;
        }
        Object enabled = map.get("enabled");
        if (enabled == null) {
            return true;
        }
        return bool(enabled);
    }

    private static boolean deletePayload(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return false;
        }
        if (bool(map.get("enabled"))) {
            return true;
        }
        return positive(map.get("minutes")) || positive(map.get("delete_after_minutes"));
    }

    private static boolean hasEntries(Object raw) {
        if (raw instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (raw instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return raw instanceof String s && !s.isBlank();
    }

    private static boolean positive(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue() > 0;
        }
        if (raw == null) {
            return false;
        }
        try {
            return Integer.parseInt(raw.toString().trim()) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && "true".equalsIgnoreCase(value.toString());
    }
}
