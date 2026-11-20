package ru.posts.api.post;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import ru.posts.api.web.dto.ord.OrdDtos;
import ru.posts.api.web.dto.post.PostDtos;
import ru.posts.ord.api.AdMarkingDefaults;

/**
 * When {@code ad=true} and {@code autoDeleteAfterPeriod} is enabled, sets variant {@code deleteAfter} to
 * {@code publishAt + displayPeriodDays} if the client omitted it (API default; publisher mirrors via
 * {@link ru.posts.publisher.service.ScheduledActionsFactory}).
 */
public final class PostAdOptionsEnricher {

    private PostAdOptionsEnricher() {}

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
            return null;
        }
        return publishAt.plus(days, ChronoUnit.DAYS);
    }

    public static List<PostDtos.PostVariantUpsert> enrichDeleteAfter(
            boolean ad, OrdDtos.AdMarking marking, Instant publishAt, List<PostDtos.PostVariantUpsert> variants) {
        if (!ad || variants == null || variants.isEmpty()) {
            return variants;
        }
        Instant deleteAfter = resolveDefaultDeleteAfter(marking, publishAt);
        if (deleteAfter == null) {
            return variants;
        }
        List<PostDtos.PostVariantUpsert> enriched = new ArrayList<>(variants.size());
        for (PostDtos.PostVariantUpsert v : variants) {
            PostDtos.PostVariantOptions options = v.options();
            if (options != null && options.deleteAfter() != null) {
                enriched.add(v);
                continue;
            }
            if (options == null) {
                options = new PostDtos.PostVariantOptions(
                        null,
                        null,
                        deleteAfter,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
            } else {
                options = copyWithDeleteAfter(options, deleteAfter);
            }
            enriched.add(new PostDtos.PostVariantUpsert(v.id(), v.channelId(), v.network(), v.content(), options));
        }
        return enriched;
    }

    private static PostDtos.PostVariantOptions copyWithDeleteAfter(
            PostDtos.PostVariantOptions options, Instant deleteAfter) {
        return new PostDtos.PostVariantOptions(
                options.pin(),
                options.unpinAt(),
                deleteAfter,
                options.mute(),
                options.closeComments(),
                options.signed(),
                options.firstComment(),
                options.buttons(),
                options.carouselMode(),
                options.donutDays(),
                options.coOwnerIds(),
                options.primaryAttachmentsMode(),
                options.poll(),
                options.geo(),
                options.copyrightUrl(),
                options.actionButton(),
                options.extra());
    }
}
