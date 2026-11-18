package ru.posts.grabber.ssrf;

public class SsrfBlockedException extends RuntimeException {

    public SsrfBlockedException(String message) {
        super(message);
    }

    public SsrfBlockedException(String message, Throwable cause) {
        super(message, cause);
    }
}
