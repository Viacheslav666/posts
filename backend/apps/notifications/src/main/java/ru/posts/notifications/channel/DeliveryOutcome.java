package ru.posts.notifications.channel;

public record DeliveryOutcome(Status status, String providerMessageId, String error) {

    public enum Status {
        SENT,
        SKIPPED,
        FAILED
    }

    public static DeliveryOutcome sent(String providerMessageId) {
        return new DeliveryOutcome(Status.SENT, providerMessageId, null);
    }

    public static DeliveryOutcome skipped(String reason) {
        return new DeliveryOutcome(Status.SKIPPED, null, reason);
    }

    public static DeliveryOutcome failed(String error) {
        return new DeliveryOutcome(Status.FAILED, null, error);
    }
}
