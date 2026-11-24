package ru.posts.grabber.publish;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.target.GrabberModeMatrix;
import ru.posts.grabber.target.GrabberTargetMode;
import ru.posts.grabber.transform.ContentTransformer;
import ru.posts.grabber.transform.MacroExpander;

/**
 * Builds VK variant options (first comment, attachments, action button, close comments, delete
 * timer) and the notices to log when a setting is skipped.
 */
public final class GrabberPublishOptions {

    public record Notice(String setting, String code, String message) {}

    public record Built(Map<String, Object> options, List<Notice> notices) {}

    private GrabberPublishOptions() {}

    public static final int ACTION_BUTTON_URL_MAX = 2048;

    public static Built build(Map<String, Object> config, MacroExpander.Context ctx, Instant scheduledAt) {
        return build(config, ctx, scheduledAt, false);
    }

    public static Built build(
            Map<String, Object> config, MacroExpander.Context ctx, Instant scheduledAt, boolean actionButtonEnabled) {
        Map<String, Object> options = new LinkedHashMap<>();
        List<Notice> notices = new ArrayList<>();
        Map<String, Object> transform = GrabberConfigSupport.transform(config);
        Map<String, Object> firstComment = GrabberConfigSupport.firstComment(config);
        Instant runAt = scheduledAt == null ? Instant.now() : scheduledAt;
        GrabberTargetMode mode = GrabberTargetMode.fromWire(GrabberConfigSupport.targetMode(config));
        int interval = GrabberConfigSupport.declaredMinIntervalMinutes(config);
        String modeLabel = mode.wire();

        Object attachmentsMode = transform.get("attachments_mode");
        if (attachmentsMode != null && !attachmentsMode.toString().isBlank()) {
            options.put("primaryAttachmentsMode", attachmentsMode.toString());
        }
        if (GrabberConfigSupport.bool(transform.get("close_comments"), false)) {
            options.put("closeComments", true);
        }

        boolean commentEnabled = GrabberConfigSupport.bool(firstComment.get("enabled"), false);
        String commentTemplate = string(firstComment.get("text"));
        String attachmentTemplate = attachmentTemplate(firstComment.get("attachments"));
        boolean commentExpandedEmpty = false;
        if (commentEnabled && !commentTemplate.isBlank()) {
            if (GrabberModeMatrix.supports(mode, interval, GrabberModeMatrix.Feature.FIRST_COMMENT)) {
                String text = ContentTransformer.firstCommentDraft(config, ctx);
                if (text != null && !text.isBlank()) {
                    options.put("firstComment", text);
                } else {
                    commentExpandedEmpty = true;
                    notices.add(new Notice(
                            "first_comment",
                            "empty_after_macros",
                            "Первый комментарий после макросов пустой и не отправлен."));
                }
            } else {
                notices.add(new Notice(
                        "first_comment",
                        "mode_postponed",
                        "Первый комментарий не публикуется в отложенном режиме (" + modeLabel + ")."));
            }
        }
        if (commentEnabled && !attachmentTemplate.isBlank()) {
            if (!GrabberModeMatrix.supports(mode, interval, GrabberModeMatrix.Feature.COMMENT_ATTACHMENTS)) {
                String why = mode.postponed() ? "отложенном режиме" : "режиме с интервалом";
                notices.add(new Notice(
                        "comment_attachments",
                        mode.postponed() ? "mode_postponed" : "mode_interval",
                        "Вложения комментария не отправляются в " + why + " (" + modeLabel + ")."));
            } else if (options.containsKey("firstComment") || commentTemplate.isBlank()) {
                CommentAttachments.ParseResult parsed = CommentAttachments.parse(attachmentTemplate, config, ctx);
                if (!parsed.invalid().isEmpty()) {
                    notices.add(new Notice(
                            "comment_attachments",
                            "invalid_attachment",
                            "Пропущены вложения комментария (нужен photo-ID_ID или video-ID_ID): "
                                    + String.join(", ", parsed.invalid())));
                }
                if (!parsed.valid().isEmpty() && options.containsKey("firstComment")) {
                    options.put("firstCommentAttachments", parsed.valid());
                } else if (!parsed.valid().isEmpty() && commentTemplate.isBlank()) {
                    notices.add(new Notice(
                            "comment_attachments",
                            "comment_text_required",
                            "Вложения комментария не отправлены: нужен текст первого комментария."));
                }
            } else if (commentExpandedEmpty) {
                CommentAttachments.ParseResult parsed = CommentAttachments.parse(attachmentTemplate, config, ctx);
                if (!parsed.invalid().isEmpty()) {
                    notices.add(new Notice(
                            "comment_attachments",
                            "invalid_attachment",
                            "Пропущены вложения комментария (нужен photo-ID_ID или video-ID_ID): "
                                    + String.join(", ", parsed.invalid())));
                }
                if (!parsed.valid().isEmpty()) {
                    notices.add(new Notice(
                            "comment_attachments",
                            "empty_after_macros",
                            "Вложения комментария не отправлены: текст первого комментария после макросов пустой."));
                }
            }
        }

        Map<String, Object> button = actionButton(transform.get("action_button"));
        if (!button.isEmpty()) {
            if (!actionButtonEnabled) {
                notices.add(new Notice(
                        "action_button", "disabled", "Кнопка действия выключена: требуется проверка на VK."));
            } else if (!GrabberModeMatrix.supports(mode, interval, GrabberModeMatrix.Feature.ACTION_BUTTON)) {
                notices.add(new Notice(
                        "action_button",
                        "mode_postponed",
                        "Кнопка действия не ставится в отложенном режиме (" + modeLabel + ")."));
            } else {
                String error = actionButtonError(button);
                if (error != null) {
                    notices.add(new Notice("action_button", "invalid_action_button", error));
                } else {
                    options.put("actionButton", button);
                }
            }
        }

        if (GrabberConfigSupport.bool(transform.get("delete_after_enabled"), false)) {
            if (!GrabberModeMatrix.supports(mode, interval, GrabberModeMatrix.Feature.SCHEDULED_DELETE)) {
                notices.add(new Notice(
                        "delete_after",
                        "mode_postponed",
                        "Таймер удаления не работает в отложенном режиме (" + modeLabel + ")."));
            } else {
                Duration delay = GrabberConfigSupport.deleteAfter(config);
                if (delay == null) {
                    notices.add(new Notice(
                            "delete_after",
                            "invalid_delete_after",
                            "Таймер удаления пропущен: укажите число и единицу minutes, hours или days."));
                } else {
                    options.put("deleteAfter", runAt.plus(delay).toString());
                }
            }
        }
        return new Built(options, List.copyOf(notices));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> actionButton(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((k, v) -> {
            if (k != null && v != null) {
                copy.put(String.valueOf(k), v);
            }
        });
        return copy;
    }

    public static String actionButtonError(Map<String, Object> button) {
        if (button == null || button.isEmpty()) {
            return null;
        }
        String type = string(button.get("type"));
        String title = string(button.get("title"));
        String url = string(button.get("url"));
        Object nested = button.get("action");
        if (nested instanceof Map<?, ?> action) {
            if (type.isBlank()) {
                type = string(action.get("type"));
            }
            if (url.isBlank()) {
                url = string(action.get("url"));
            }
        }
        if (type.isBlank()) {
            type = "open_url";
        }
        if (!"open_url".equalsIgnoreCase(type)) {
            return "Кнопка действия поддерживает только тип open_url.";
        }
        if (title.isBlank() || title.length() > 40) {
            return "Текст кнопки действия: от 1 до 40 символов.";
        }
        if (!url.matches("(?i)https?://\\S+")) {
            return "Ссылка кнопки действия должна начинаться с http:// или https://.";
        }
        if (url.length() > ACTION_BUTTON_URL_MAX) {
            return "Ссылка кнопки действия длиннее 2048 символов.";
        }
        return null;
    }

    private static String attachmentTemplate(Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof String s) {
            return s.trim();
        }
        if (raw instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item == null || item.toString().isBlank()) {
                    continue;
                }
                if (!sb.isEmpty()) {
                    sb.append(',');
                }
                sb.append(item);
            }
            return sb.toString();
        }
        return raw.toString().trim();
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
