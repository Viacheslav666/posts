package ru.posts.api.channel;

/** Raised when RabbitMQ cannot accept an inbound social event (hooks return 503). */
public class IngestBrokerUnavailableException extends RuntimeException {

    public IngestBrokerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
