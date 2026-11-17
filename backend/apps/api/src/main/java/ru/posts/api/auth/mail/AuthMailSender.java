package ru.posts.api.auth.mail;

public interface AuthMailSender {

    void send(String to, String subject, String textBody);
}
