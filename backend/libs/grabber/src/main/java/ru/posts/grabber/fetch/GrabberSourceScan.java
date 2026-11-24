package ru.posts.grabber.fetch;

import java.util.ArrayList;
import java.util.List;

/** Fetches grabber sources one by one so a single failure does not abort the run. */
public final class GrabberSourceScan {

    private GrabberSourceScan() {}

    public static <T> List<T> collect(List<String> sourceIds, SourceFetch<T> fetch, SourceFailure onFailure) {
        List<T> all = new ArrayList<>();
        if (sourceIds == null || sourceIds.isEmpty()) {
            return all;
        }
        for (String sourceId : sourceIds) {
            try {
                List<T> part = fetch.fetch(sourceId);
                if (part != null) {
                    all.addAll(part);
                }
            } catch (RuntimeException ex) {
                if (onFailure != null) {
                    onFailure.failed(sourceId, ex);
                }
            }
        }
        return all;
    }

    /** Removes an access token from a log or error line. */
    public static String redact(String message, String secret) {
        if (message == null || message.isBlank()) {
            return "";
        }
        if (secret == null || secret.isBlank()) {
            return message;
        }
        return message.replace(secret, "[redacted]");
    }

    @FunctionalInterface
    public interface SourceFetch<T> {
        List<T> fetch(String sourceId);
    }

    @FunctionalInterface
    public interface SourceFailure {
        void failed(String sourceId, RuntimeException ex);
    }
}
