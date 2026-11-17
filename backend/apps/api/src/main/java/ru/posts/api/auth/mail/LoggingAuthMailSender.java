package ru.posts.api.auth.mail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Capturing sender for tests / openapi / environments without SMTP. */
public class LoggingAuthMailSender implements AuthMailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingAuthMailSender.class);

    private final CopyOnWriteArrayList<MailMessage> messages = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String textBody) {
        MailMessage message = new MailMessage(to, subject, textBody);
        messages.add(message);
        log.info("Auth mail to={} subject={} body={}", to, subject, textBody);
    }

    public List<MailMessage> messages() {
        return List.copyOf(messages);
    }

    public void clear() {
        messages.clear();
    }

    public record MailMessage(String to, String subject, String body) {}
}
