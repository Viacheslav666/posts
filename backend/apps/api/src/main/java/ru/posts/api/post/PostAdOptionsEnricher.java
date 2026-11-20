package ru.posts.api.post;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import ru.posts.api.web.dto.ord.OrdDtos;
import ru.posts.ord.api.AdMarkingDefaults;

/**
 * ORD ad auto-delete is computed per publication at publish time in
 * {@link ru.posts.publisher.service.ScheduledActionsFactory} from {@code runAt + displayPeriodDays}
 * (see {@link AdMarkingDefaults#DEFAULT_DISPLAY_PERIOD_DAYS} when creative days are absent). Variant
 * {@code deleteAfter} is only persisted when the client sets it explicitly.
 */
public final class PostAdOptionsEnricher {

    private PostAdOptionsEnricher() {}

    /** Formula mirrored in publisher {@code ScheduledActionsFactory} (not stored in variant options). */
    public static Instant resolveDefaultDeleteAfter(OrdDtos.AdMarking marking, Instant publishAt) {
        if (marking == null || publishAt == null) {
            return null;
        }
        if (Boolean.FALSE.equals(marking.autoDeleteAfterPeriod())) {
            return null;
        }
        int days = marking.displayPeriodDays() != null
                ? marking.displayPeriodDays()
                : AdMarkingDefaults.DEFAULT_DISPLAY_PERIOD_DAYS;
        if (days <= 0) {
            days = AdMarkingDefaults.DEFAULT_DISPLAY_PERIOD_DAYS;
        }
        return publishAt.plus(days, ChronoUnit.DAYS);
    }
}
