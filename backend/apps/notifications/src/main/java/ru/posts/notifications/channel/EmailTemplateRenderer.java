package ru.posts.notifications.channel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class EmailTemplateRenderer {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String render(String eventType, Map<String, String> vars) {
        String template = loadTemplate(eventType);
        String html = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            html = html.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return html;
    }

    public String subject(String eventType) {
        return switch (eventType == null ? "" : eventType) {
            case "publication.failed" -> "Ошибка публикации";
            case "publication.succeeded" -> "Публикация выполнена";
            case "channel.reauth_required" -> "Требуется повторная авторизация канала";
            case "channel.no_permissions" -> "Недостаточно прав у канала";
            case "grabber.moderation_queued" -> "Материал на модерации";
            case "grabber.setting_error" -> "Ошибка настройки граббера";
            case "ord.error", "ord.erid_failed", "ord.problem" -> "Ошибка ОРД / erid";
            case "ord.expiry_warning" -> "Скоро окончание срока показа рекламы";
            case "ord.display_ended" -> "Рекламный пост снят с показа";
            case "invitation.created" -> "Приглашение в workspace";
            case "team.approval_requested" -> "Запрос на согласование";
            case "billing.low_balance" -> "Мало средств на балансе";
            case "billing.access_suspended" -> "Доступ приостановлен";
            default -> "Уведомление Posts";
        };
    }

    private String loadTemplate(String eventType) {
        String name = templateName(eventType);
        return cache.computeIfAbsent(name, this::readClasspath);
    }

    private static String templateName(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "generic.html";
        }
        return eventType.replace('.', '_') + ".html";
    }

    private String readClasspath(String name) {
        ClassPathResource resource = new ClassPathResource("templates/email/" + name);
        if (!resource.exists()) {
            resource = new ClassPathResource("templates/email/generic.html");
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "<html><body><p>{{title}}</p><p>{{body}}</p><p><a href=\"{{unsubscribeUrl}}\">Отписаться</a></p></body></html>";
        }
    }
}
