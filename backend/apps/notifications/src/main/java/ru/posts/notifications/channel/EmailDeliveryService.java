package ru.posts.notifications.channel;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import ru.posts.common.uuid.UuidV7;
import ru.posts.messaging.notify.NotifyDeliveryCommand;
import ru.posts.notifications.config.NotificationsProperties;
import ru.posts.persistence.entity.NotificationUnsubscribeTokenEntity;
import ru.posts.persistence.entity.UserEntity;
import ru.posts.persistence.repository.NotificationUnsubscribeTokenRepository;
import ru.posts.persistence.repository.UserRepository;

@Service
public class EmailDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final NotificationUnsubscribeTokenRepository tokenRepository;
    private final EmailTemplateRenderer templates;
    private final NotificationsProperties properties;

    public EmailDeliveryService(
            JavaMailSender mailSender,
            UserRepository userRepository,
            NotificationUnsubscribeTokenRepository tokenRepository,
            EmailTemplateRenderer templates,
            NotificationsProperties properties) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.templates = templates;
        this.properties = properties;
    }

    public DeliveryOutcome deliver(NotifyDeliveryCommand command) {
        Optional<UserEntity> userOpt = userRepository.findById(command.userId());
        if (userOpt.isEmpty()) {
            return DeliveryOutcome.skipped("user_missing");
        }
        UserEntity user = userOpt.get();
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return DeliveryOutcome.skipped("email_missing");
        }

        try {
            String token = createUnsubscribeToken(user.getId(), command.eventType());
            String unsubscribeUrl = properties.appBaseUrl() + "/unsubscribe?token=" + token;
            String greetingName = user.getName() == null || user.getName().isBlank() ? "" : ", " + user.getName();
            Map<String, String> vars = Map.of(
                    "title",
                    templates.subject(command.eventType()),
                    "body",
                    bodyText(command),
                    "userName",
                    greetingName,
                    "unsubscribeUrl",
                    unsubscribeUrl,
                    "appBaseUrl",
                    properties.appBaseUrl());
            String html = templates.render(command.eventType(), vars);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(user.getEmail());
            helper.setFrom(properties.mail().from(), properties.mail().fromName());
            helper.setSubject(templates.subject(command.eventType()));
            helper.setText(html, true);
            message.setHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");
            mailSender.send(message);
            return DeliveryOutcome.sent("smtp:" + user.getEmail());
        } catch (Exception ex) {
            log.warn("Email delivery failed for {}: {}", command.deliveryId(), ex.toString());
            return DeliveryOutcome.failed(ex.getMessage());
        }
    }

    private String createUnsubscribeToken(UUID userId, String eventType) throws Exception {
        String raw = UuidV7.generate().toString().replace("-", "")
                + UuidV7.generate().toString().replace("-", "");
        String hash = sha256(raw);
        NotificationUnsubscribeTokenEntity entity = new NotificationUnsubscribeTokenEntity();
        entity.setUserId(userId);
        entity.setTokenHash(hash);
        entity.setEventType(eventType);
        entity.setCreatedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plus(90, ChronoUnit.DAYS));
        tokenRepository.save(entity);
        return raw;
    }

    private static String bodyText(NotifyDeliveryCommand command) {
        Object result = command.payload() == null ? null : command.payload().get("result");
        Object network = command.payload() == null ? null : command.payload().get("network");
        StringBuilder sb = new StringBuilder();
        sb.append("Тип события: ").append(command.eventType()).append('.');
        if (network != null) {
            sb.append(" Сеть: ").append(network).append('.');
        }
        if (result != null) {
            sb.append(" Результат: ").append(result).append('.');
        }
        return sb.toString();
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
