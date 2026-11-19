package ru.posts.social.api;

import java.util.Map;

/** Outcome of a single remote moderation call. */
public record ModerationResult(String remoteId, Map<String, Object> raw, boolean alreadyGone) {

    public ModerationResult {
        raw = raw == null ? Map.of() : Map.copyOf(raw);
    }

    public ModerationResult(String remoteId) {
        this(remoteId, Map.of(), false);
    }

    public ModerationResult(String remoteId, Map<String, Object> raw) {
        this(remoteId, raw, false);
    }

    public static ModerationResult alreadyGone(String remoteId) {
        return new ModerationResult(remoteId, Map.of(), true);
    }
}
