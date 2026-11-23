package ru.posts.grabber.target;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.schedule.WorkingWindow;

/**
 * Resolves when a grabbed item is handed to the publication pipeline and, for postponed VK modes,
 * the native {@code publish_date} (unix seconds, at most {@link GrabberModeMatrix#POSTPONED_HORIZON_DAYS}
 * days ahead of now, inside the working window when one is configured).
 */
public final class GrabberPublishSchedule {

    /** VK rejects a publish_date that is not strictly in the future. */
    public static final int POSTPONED_MIN_LEAD_SECONDS = 90;

    /** Technical spacing so postponed posts in one batch do not share a timestamp. Not a user interval. */
    public static final int POSTPONED_SLOT_SECONDS = 60;

    private GrabberPublishSchedule() {}

    public record Plan(Instant pipelineRunAt, Long vkPublishDateEpoch) {}

    /**
     * Moves the first slot of a run past the previous accepted item when an interval is configured.
     * Postponed modes ignore the interval (the API rejects a non-zero value).
     */
    public static Instant spacedBase(Map<String, Object> config, Instant now, Instant lastAcceptedAt) {
        Instant clock = now == null ? Instant.now() : now;
        if (GrabberTargetMode.isPostponed(GrabberConfigSupport.targetMode(config))) {
            return clock;
        }
        int interval = GrabberConfigSupport.minIntervalMinutes(config);
        if (interval <= 0 || lastAcceptedAt == null) {
            return clock;
        }
        Instant next = lastAcceptedAt.plus(interval, ChronoUnit.MINUTES);
        return next.isAfter(clock) ? next : clock;
    }

    public static Plan plan(Map<String, Object> config, Instant now, Instant requested, int slotIndex) {
        Instant clock = now == null ? Instant.now() : now;
        int slot = Math.max(0, slotIndex);
        String mode = GrabberConfigSupport.targetMode(config);
        if (GrabberTargetMode.isPostponed(mode)) {
            Instant earliest = clock.plusSeconds(POSTPONED_MIN_LEAD_SECONDS + (long) POSTPONED_SLOT_SECONDS * slot);
            Instant slotAt = WorkingWindow.nextOpen(config, earliest);
            Instant horizon = clock.plus(GrabberModeMatrix.POSTPONED_HORIZON_DAYS, ChronoUnit.DAYS);
            if (slotAt.isAfter(horizon)) {
                throw new PostponedHorizonException("Отложенная запись VK должна быть не позже чем через "
                        + GrabberModeMatrix.POSTPONED_HORIZON_DAYS
                        + " дней. Ближайшее окно работы позже этого срока");
            }
            return new Plan(clock, slotAt.getEpochSecond());
        }
        Instant base = requested == null || requested.isBefore(clock) ? clock : requested;
        int interval = GrabberConfigSupport.minIntervalMinutes(config);
        if (interval > 0 && slot > 0) {
            base = base.plus((long) interval * slot, ChronoUnit.MINUTES);
        }
        Instant runAt = WorkingWindow.nextOpen(config, base);
        return new Plan(runAt, null);
    }
}
