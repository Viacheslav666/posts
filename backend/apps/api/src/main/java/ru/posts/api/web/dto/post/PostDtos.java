package ru.posts.api.web.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.posts.api.web.dto.common.ApiEnums;

public final class PostDtos {

    private PostDtos() {}

    @Schema(name = "PostContent", description = "Canonical editor content (Tiptap-compatible JSON)")
    public record PostContent(
            @Schema(description = "Plain/HTML text or structured doc") String text,
            @Schema(description = "Rich entities / Tiptap JSON", nullable = true) Map<String, Object> doc,
            List<UUID> mediaIds) {}

    @Schema(name = "PostVariantOptions")
    public record PostVariantOptions(
            Boolean pin,
            Instant unpinAt,
            Instant deleteAfter,
            Boolean mute,
            Boolean closeComments,
            Boolean signed,
            String firstComment,
            List<Map<String, Object>> buttons,
            String carouselMode,
            Map<String, Object> extra) {}

    @Schema(name = "PostVariant")
    public record PostVariant(
            UUID id, UUID channelId, ApiEnums.Network network, PostContent content, PostVariantOptions options) {}

    @Schema(name = "Recurrence")
    public record Recurrence(
            @Schema(example = "weekly") String freq,
            @Schema(example = "1") Integer interval,
            List<String> byWeekday,
            Instant until) {}

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
            boolean ad,
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
            boolean ad,
            @Valid List<PostVariantUpsert> variants) {}

    @Schema(name = "UpdatePostRequest")
    public record UpdatePostRequest(
            UUID projectId,
            @Size(max = 200) String title,
            @Valid PostContent baseContent,
            Instant scheduledAt,
            @Valid Recurrence recurrence,
            Boolean ad,
            @NotNull @Schema(description = "Optimistic lock version") Integer version,
            @Valid List<PostVariantUpsert> variants) {}

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
            @NotNull @Schema(description = "Optimistic lock version") Integer version) {}

    @Schema(name = "PostActionRequest")
    public record PostActionRequest(@NotNull Integer version) {}

    @Schema(name = "BulkPostsRequest")
    public record BulkPostsRequest(
            @NotEmpty List<UUID> postIds,
            @NotNull @Schema(allowableValues = {"pause", "resume", "delete", "reschedule"}) String action,
            Instant scheduledAt) {}

    @Schema(name = "BulkPostsResponse")
    public record BulkPostsResponse(int affected, List<UUID> failedIds) {}

    @Schema(name = "PreviewRequest")
    public record PreviewRequest(
            @NotNull ApiEnums.Network network, @NotNull @Valid PostContent content, PostVariantOptions options) {}

    @Schema(name = "PreviewResponse")
    public record PreviewResponse(
            ApiEnums.Network network,
            String html,
            String plainText,
            boolean valid,
            List<String> warnings,
            List<ValidationIssue> issues) {}

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
            List<UUID> channelIds) {}

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

    @Schema(name = "MoveCalendarItemRequest")
    public record MoveCalendarItemRequest(@NotNull Instant scheduledAt, @NotNull Integer version) {}
}
