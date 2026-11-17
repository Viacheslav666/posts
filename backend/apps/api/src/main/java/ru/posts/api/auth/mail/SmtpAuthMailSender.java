package ru.posts.api.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import ru.posts.api.auth.AuthProperties;

public class SmtpAuthMailSender implements AuthMailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpAuthMailSender.class);

    private final JavaMailSender mailSender;
    private final AuthProperties properties;

    public SmtpAuthMailSender(JavaMailSender mailSender, AuthProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String to, String subject, String textBody) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMailFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(textBody);
        mailSender.send(message);
        log.debug("Sent auth mail to {}", to);
    }
}
