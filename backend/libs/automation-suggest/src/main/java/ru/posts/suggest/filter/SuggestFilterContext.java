package ru.posts.suggest.filter;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Precomputed counters and author/channel signals for pure filter evaluation.
 *
 * <p>{@link #authorMessagesAllowed} / {@link #authorProfileClosed} are nullable when VK lookup was
 * skipped. {@link #adsPauseActive} is set by the scheduler when the min wait after ads has not
 * elapsed — the engine then returns {@code FILTER_OUT pause_after_ads}.
 */
public record SuggestFilterContext(
        Instant now,
        int publishedLastHour,
        int publishedLastDay,
        int publishedLastWeek,
        int authorPublishedLastHour,
        int authorPublishedLastDay,
        int authorPublishedLastWeek,
        boolean lastWallIsAds,
        Boolean authorMessagesAllowed,
        Boolean authorProfileClosed,
        boolean nearDuplicate,
        ZoneId zone,
        boolean adsPauseActive) {

    public SuggestFilterContext {
        now = now == null ? Instant.now() : now;
        zone = zone == null ? ZoneId.of("Europe/Moscow") : zone;
    }

    public static SuggestFilterContext empty(Instant now) {
        return builder().now(now).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Instant now = Instant.now();
        private int publishedLastHour;
        private int publishedLastDay;
        private int publishedLastWeek;
        private int authorPublishedLastHour;
        private int authorPublishedLastDay;
        private int authorPublishedLastWeek;
        private boolean lastWallIsAds;
        private Boolean authorMessagesAllowed;
        private Boolean authorProfileClosed;
        private boolean nearDuplicate;
        private ZoneId zone = ZoneId.of("Europe/Moscow");
        private boolean adsPauseActive;

        public Builder now(Instant now) {
            this.now = now;
            return this;
        }

        public Builder publishedLastHour(int value) {
            this.publishedLastHour = value;
            return this;
        }

        public Builder publishedLastDay(int value) {
            this.publishedLastDay = value;
            return this;
        }

        public Builder publishedLastWeek(int value) {
            this.publishedLastWeek = value;
            return this;
        }

        public Builder authorPublishedLastHour(int value) {
            this.authorPublishedLastHour = value;
            return this;
        }

        public Builder authorPublishedLastDay(int value) {
            this.authorPublishedLastDay = value;
            return this;
        }

        public Builder authorPublishedLastWeek(int value) {
            this.authorPublishedLastWeek = value;
            return this;
        }

        public Builder lastWallIsAds(boolean value) {
            this.lastWallIsAds = value;
            return this;
        }

        public Builder authorMessagesAllowed(Boolean value) {
            this.authorMessagesAllowed = value;
            return this;
        }

        public Builder authorProfileClosed(Boolean value) {
            this.authorProfileClosed = value;
            return this;
        }

        public Builder nearDuplicate(boolean value) {
            this.nearDuplicate = value;
            return this;
        }

        public Builder zone(ZoneId zone) {
            this.zone = zone;
            return this;
        }

        public Builder zone(String timezone) {
            this.zone = ZoneId.of(timezone);
            return this;
        }

        public Builder adsPauseActive(boolean value) {
            this.adsPauseActive = value;
            return this;
        }

        public SuggestFilterContext build() {
            return new SuggestFilterContext(
                    now,
                    publishedLastHour,
                    publishedLastDay,
                    publishedLastWeek,
                    authorPublishedLastHour,
                    authorPublishedLastDay,
                    authorPublishedLastWeek,
                    lastWallIsAds,
                    authorMessagesAllowed,
                    authorProfileClosed,
                    nearDuplicate,
                    zone,
                    adsPauseActive);
        }
    }
}
