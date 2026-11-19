package ru.posts.suggest.schedule;

import java.time.Instant;

/**
 * Pause anchor is the later of the last suggest publication and the last wall publication.
 * When that later post is an ad, the pause is measured from the ad itself.
 */
public final class SuggestAdsPause {

    private SuggestAdsPause() {}

    public record Decision(boolean lastWallWasAds, Instant anchor) {}

    public static Decision decide(Instant suggestAt, boolean suggestIsAd, Instant wallAt, boolean wallIsAd) {
        boolean wallLater = wallAt != null && (suggestAt == null || wallAt.isAfter(suggestAt));
        if (wallLater) {
            return new Decision(wallIsAd, wallAt);
        }
        return new Decision(suggestIsAd && suggestAt != null, suggestAt);
    }
}
