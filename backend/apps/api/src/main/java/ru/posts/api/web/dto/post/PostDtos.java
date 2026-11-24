package ru.posts.api.web.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.ord.OrdDtos;

public final class PostDtos {

    private PostDtos() {}

    @Schema(name = "PostContent", description = "Canonical editor content (Tiptap-compatible JSON)")
    public record PostContent(
            @Schema(description = "Plain/HTML text or structured doc") String text,
            @Schema(description = "Rich entities / Tiptap JSON", nullable = true) Map<String, Object> doc,
            List<UUID> mediaIds,
            @Schema(
                            description =
                                    "VK string attachments (photo-1_2, video…, doc…, clip…). Validated on create/update.",
                            nullable = true)
                    List<String> stringAttachments) {
        public PostContent(String text, Map<String, Object> doc, List<UUID> mediaIds) {
            this(text, doc, mediaIds, null);
        }
    }

    @Schema(name = "PollOptions")
    public record PollOptions(@Schema(example = "Ваш выбор?") String question, @Size(min = 2) List<String> answers) {}

    @Schema(name = "GeoPoint")
    public record GeoPoint(Double lat, Double lon) {}

    @Schema(name = "PostVariantOptions")
    public record PostVariantOptions(
            Boolean pin,
            Instant unpinAt,
            Instant deleteAfter,
            Boolean mute,
            Boolean closeComments,
            Boolean signed,
            String firstComment,
            List<InlineButton> buttons,
            String carouselMode,
            @Schema(description = "VK Donut visibility days 0–7") @Min(0) @Max(7) Integer donutDays,
            @Schema(description = "VK co-owner community ids (≤4)") List<String> coOwnerIds,
            @Schema(description = "grid | carousel", example = "grid") String primaryAttachmentsMode,
            PollOptions poll,
            GeoPoint geo,
            String copyrightUrl,
            @Schema(description = "VK action button payload") Map<String, Object> actionButton,
            Map<String, Object> extra) {}

    @Schema(name = "PostVariant")
    public record PostVariant(
            UUID id, UUID channelId, ApiEnums.Network network, PostContent content, PostVariantOptions options) {}

    @Schema(
            name = "Recurrence",
            description = "Recurrence rule. Optional times[] (HH:mm in post timezone) expands multiple runs per day; "
                    + "when omitted, the time-of-day from scheduledAt is used (backward compatible).")
    public record Recurrence(
            @NotNull ApiEnums.RecurrenceFreq freq,
            @Schema(example = "1") Integer interval,
            List<String> byWeekday,
            Instant until,
            @Schema(
                            description = "Clock times in post timezone, format HH:mm (e.g. 09:00, 18:30)",
                            example = "[\"09:00\", \"18:30\"]",
                            nullable = true)
                    List<@jakarta.validation.constraints.Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String>
                            times) {
        public Recurrence(ApiEnums.RecurrenceFreq freq, Integer interval, List<String> byWeekday, Instant until) {
            this(freq, interval, byWeekday, until, null);
        }
    }

    @Schema(
            name = "RepostTarget",
            description = "Repost of an existing network object with optional comment (US-4.9). "
                    + "Supported for vk/tg/ok; not for max (capability.repost=false).")
    public record RepostTarget(
            @NotNull ApiEnums.Network network,
            @Schema(description = "External post/object id in the source network") String externalId,
            @Schema(description = "Canonical URL of the source post when id is unknown") String url,
            @Schema(description = "Optional commentary attached to the repost") String comment) {}

    @Schema(name = "InlineButton")
    public record InlineButton(
            @Schema(example = "Открыть") String text,
            @Schema(example = "https://example.com") String url,
            @Schema(description = "Callback data for bot buttons", nullable = true) String callbackData) {}

    @Schema(
            name = "AdMarkingSummary",
            description = "Lightweight ad marking card fields for list/calendar (full ORD in BE-15).")
    public record AdMarkingSummary(
            boolean enabled, @Schema(nullable = true) String erid, @Schema(nullable = true) String advertiserName) {}

    @Schema(name = "PostSort", description = "Sort order for postsList (FE-8)")
    public enum PostSort {
        scheduledAt_asc,
        scheduledAt_desc,
        title_asc,
        updatedAt_desc
    }

    @Schema(name = "ContentKind")
    public enum ContentKind {
        post,
        story
    }

    @Schema(name = "StoryOptions", description = "Story-specific options (BE-16 / E11 / BE-24)")
    public record StoryOptions(
            ApiEnums.StoryTtl ttl,
            @Schema(description = "VK/OK CTA key", example = "go_to") String linkText,
            @Schema(example = "https://vk.com/club1") String linkUrl,
            @Schema(description = "photo | video") String mediaKind,
            @Min(0) @Max(262920) Integer autoDeleteAfterMinutes,
            Instant autoDeleteAt,
            Boolean deleteAfterPublish,
            @Schema(description = "OK effective TTL after degradation (always ttl_24h)") ApiEnums.StoryTtl effectiveTtl,
            List<String> warnings,
            @Valid OrdDtos.AdMarking ads,
            @Schema(description = "VK stories.add_to_news (default true)") Boolean addToNews) {}

    @Schema(name = "Post")
    public record Post(
            UUID id,
            UUID workspaceId,
            UUID projectId,
            UUID authorId,
            String title,
            PostContent baseContent,
            ApiEnums.PostStatus status,
            Instant scheduledAt,
            Recurrence recurrence,
            @Schema(description = "Repost mode payload; null for regular posts") RepostTarget repost,
            boolean ad,
            @Schema(description = "Lightweight ad card; null when ad=false") AdMarkingSummary adMarking,
            ContentKind contentKind,
            @Schema(description = "Present when contentKind=story") StoryOptions story,
            @Schema(
                            description = "manual|grabber|rss|import|api - grabber/rss set by BE-17",
                            example = "manual",
                            allowableValues = {"manual", "grabber", "rss", "import", "api"})
                    String sourceType,
            @Schema(nullable = true) Map<String, Object> sourceRef,
            @Schema(nullable = true) Instant stopAt,
            boolean removeAfterPublish,
            @Schema(example = "Europe/Moscow") String timezone,
            int version,
            List<PostVariant> variants,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "CreatePostRequest")
    public record CreatePostRequest(
            @NotNull UUID workspaceId,
            UUID projectId,
            @Size(max = 200) String title,
            @NotNull @Valid PostContent baseContent,
            Instant scheduledAt,
            @Valid Recurrence recurrence,
            @Valid RepostTarget repost,
            boolean ad,
            @Schema(description = "Typed ad marking; preferred over adPayload") @Valid OrdDtos.AdMarking adMarking,
            @Schema(
                            description = "Deprecated: raw jsonb snapshot; prefer adMarking",
                            deprecated = true,
                            nullable = true)
                    Map<String, Object> adPayload,
            Instant stopAt,
            Boolean removeAfterPublish,
            @Schema(example = "Europe/Moscow") String timezone,
            ContentKind contentKind,
            @Valid StoryOptions story,
            @Valid List<PostVariantUpsert> variants) {}

    @Schema(name = "UpdatePostRequest")
    public record UpdatePostRequest(
            UUID projectId,
            @Size(max = 200) String title,
            @Valid PostContent baseContent,
            Instant scheduledAt,
            @Valid Recurrence recurrence,
            @Valid RepostTarget repost,
            Boolean ad,
            @Schema(description = "Typed ad marking; preferred over adPayload") @Valid OrdDtos.AdMarking adMarking,
            @Schema(
                            description = "Deprecated: raw jsonb snapshot; prefer adMarking",
                            deprecated = true,
                            nullable = true)
                    Map<String, Object> adPayload,
            Instant stopAt,
            Boolean removeAfterPublish,
            @Schema(example = "Europe/Moscow") String timezone,
            @Valid StoryOptions story,
            @NotNull @Schema(description = "Optimistic lock version") Integer version,
            @Valid List<PostVariantUpsert> variants) {}

    @Schema(name = "BatchStoriesRequest")
    public record BatchStoriesRequest(
            @NotNull UUID workspaceId,
            UUID projectId,
            @NotEmpty List<UUID> channelIds,
            @NotEmpty List<UUID> mediaIds,
            @NotNull Instant scheduledAt,
            @Schema(description = "Minutes between consecutive stories", example = "5") @Min(0) Integer stepMinutes,
            @Valid StoryOptions story,
            @Schema(example = "Europe/Moscow") String timezone,
            boolean ad,
            @Valid OrdDtos.AdMarking adMarking) {}

    @Schema(name = "BatchStoriesResponse")
    public record BatchStoriesResponse(int created, List<UUID> postIds, List<BatchStoryFailure> failures) {}

    @Schema(name = "BatchStoryFailure")
    public record BatchStoryFailure(UUID mediaId, String code, String message) {}

    @Schema(
            name = "BatchPostsRequest",
            description = "Parity P16: batch-create posts from media (images/videos/clips), one post per media item.")
    public record BatchPostsRequest(
            @NotNull UUID workspaceId,
            UUID projectId,
            @NotEmpty List<UUID> channelIds,
            @NotEmpty List<UUID> mediaIds,
            @NotNull Instant scheduledAt,
            @Schema(description = "Minutes between consecutive posts", example = "5") @Min(0) Integer stepMinutes,
            @Schema(description = "Optional shared text / variable template for each post") String text,
            @Schema(description = "Optional VK string attachments applied to every post", nullable = true)
                    List<String> stringAttachments,
            @Valid Recurrence recurrence,
            @Schema(example = "Europe/Moscow") String timezone,
            boolean ad,
            @Valid OrdDtos.AdMarking adMarking,
            @Valid PostVariantOptions options) {}

    @Schema(name = "BatchPostsResponse")
    public record BatchPostsResponse(int created, List<UUID> postIds, List<BatchPostFailure> failures) {}

    @Schema(name = "BatchPostFailure")
    public record BatchPostFailure(UUID mediaId, String code, String message) {}

    @Schema(name = "PostVariantUpsert")
    public record PostVariantUpsert(
            UUID id,
            @NotNull UUID channelId,
            @NotNull ApiEnums.Network network,
            @Valid PostContent content,
            @Valid PostVariantOptions options) {}

    @Schema(name = "SchedulePostRequest")
    public record SchedulePostRequest(
            @NotNull Instant scheduledAt,
            @Valid Recurrence recurrence,
            @Schema(example = "Europe/Moscow") String timezone,
            @NotNull @Schema(description = "Optimistic lock version") Integer version) {}

    @Schema(name = "PostActionRequest")
    public record PostActionRequest(@NotNull Integer version) {}

    @Schema(name = "BulkPostsRequest")
    public record BulkPostsRequest(
            @NotEmpty List<UUID> postIds,
            @NotNull
                    @Schema(
                            allowableValues = {"pause", "resume", "delete", "reschedule", "duplicate"},
                            description = "duplicate accepted per FE-8 contract proposal")
                    String action,
            Instant scheduledAt) {}

    @Schema(name = "BulkPostsResponse")
    public record BulkPostsResponse(int affected, List<UUID> failedIds) {}

    @Schema(
            name = "PreviewRequest",
            description = "Server-side render/validate. Expands editor variables ({date}, {time}, …), [rand] spintax, "
                    + "and validates VK string attachments. Pass timezone/at/group*/json/birthdays for "
                    + "editor preview parity with publish-time rendering.")
    public record PreviewRequest(
            @NotNull ApiEnums.Network network,
            @NotNull @Valid PostContent content,
            PostVariantOptions options,
            boolean ad,
            @Valid OrdDtos.AdMarking adMarking,
            @Schema(description = "Optional erid for preview marker") String erid,
            @Schema(description = "Post timezone for {date}/{time}/…", example = "Europe/Moscow", nullable = true)
                    String timezone,
            @Schema(description = "Instant used for date/time variables; defaults to now", nullable = true) Instant at,
            @Schema(description = "Channel external id for {group_id}", nullable = true) String groupId,
            @Schema(description = "Channel title for {group_name}", nullable = true) String groupName,
            @Schema(description = "Pre-resolved birthday list for {birthdays}/{именинники}", nullable = true)
                    String birthdays,
            @Schema(description = "JSON map for {json:path} variables", nullable = true) Map<String, Object> json,
            @Schema(
                            description =
                                    "Stable seed for [rand] (defaults to content hash). Retries with the same seed "
                                            + "must expand to the same text.",
                            nullable = true)
                    String spintaxSeed) {
        public PreviewRequest(
                ApiEnums.Network network,
                PostContent content,
                PostVariantOptions options,
                boolean ad,
                OrdDtos.AdMarking adMarking,
                String erid) {
            this(network, content, options, ad, adMarking, erid, null, null, null, null, null, null, null);
        }
    }

    @Schema(name = "PreviewResponse")
    public record PreviewResponse(
            ApiEnums.Network network,
            String html,
            String plainText,
            boolean valid,
            List<String> warnings,
            List<ValidationIssue> issues,
            @Schema(description = "Text after variable/[rand] expansion, before network HTML filter", nullable = true)
                    String expandedText) {
        public PreviewResponse(
                ApiEnums.Network network,
                String html,
                String plainText,
                boolean valid,
                List<String> warnings,
                List<ValidationIssue> issues) {
            this(network, html, plainText, valid, warnings, issues, null);
        }
    }

    @Schema(name = "ValidationIssue")
    public record ValidationIssue(
            @Schema(example = "text.max_chars") String code,
            @Schema(example = "error") String severity,
            String message,
            String field) {}

    @Schema(name = "CalendarItem", description = "Lightweight calendar cell DTO")
    public record CalendarItem(
            UUID id,
            String title,
            ApiEnums.PostStatus status,
            Instant scheduledAt,
            List<ApiEnums.Network> networks,
            UUID projectId,
            List<UUID> channelIds,
            ContentKind contentKind,
            boolean ad,
            @Schema(nullable = true) AdMarkingSummary adMarking,
            UUID authorId) {}

    @Schema(name = "Publication")
    public record Publication(
            UUID id,
            UUID workspaceId,
            UUID postId,
            UUID variantId,
            UUID channelId,
            ApiEnums.Network network,
            Instant runAt,
            ApiEnums.PublicationState state,
            int attempts,
            Instant nextAttemptAt,
            String remotePostId,
            String remoteUrl,
            String errorCode,
            String errorMessage,
            Instant publishedAt,
            Instant createdAt) {}

    @Schema(name = "PublicationAttempt", description = "Single publish attempt timeline entry (FE-8)")
    public record PublicationAttempt(
            UUID id,
            UUID publicationId,
            int attemptNo,
            Instant startedAt,
            Instant finishedAt,
            Integer httpStatus,
            String errorCode,
            Map<String, Object> responseExcerpt) {}

    @Schema(name = "MoveCalendarItemRequest")
    public record MoveCalendarItemRequest(
            @NotNull Instant scheduledAt,
            @Schema(example = "Europe/Moscow") String timezone,
            @NotNull Integer version) {}
}
