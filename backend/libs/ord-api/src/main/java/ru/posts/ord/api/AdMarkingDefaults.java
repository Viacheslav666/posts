package ru.posts.ord.api;

/** Shared ORD ad display / auto-delete defaults (E10 / scheduled-posts-and-ad-marking §3.4). */
public final class AdMarkingDefaults {

    public static final int DEFAULT_DISPLAY_PERIOD_DAYS = 30;
    public static final long SECONDS_PER_DAY = 86_400L;

    private AdMarkingDefaults() {}
}
