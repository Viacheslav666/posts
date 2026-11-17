package ru.posts.api.web.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** Shared API enumerations aligned with Flyway checks and product glossary. */
public final class ApiEnums {

    private ApiEnums() {}

    @Schema(description = "Social network identifier (DB / publish routing key)")
    public enum Network {
        vk,
        tg,
        ok,
        max,
        rutube
    }

    @Schema(description = "Workspace member role")
    public enum MemberRole {
        owner,
        admin,
        editor,
        author,
        viewer
    }

    @Schema(description = "Channel connection status")
    public enum ChannelStatus {
        active,
        reauth_required,
        no_permissions,
        disabled
    }

    @Schema(description = "Channel entity type within a network")
    public enum ChannelType {
        group,
        page,
        channel,
        chat,
        profile
    }

    @Schema(description = "Aggregate post status")
    public enum PostStatus {
        draft,
        pending_approval,
        approved,
        scheduled,
        publishing,
        published,
        partially_published,
        failed,
        paused,
        archived
    }

    @Schema(description = "Per-channel publication pipeline state")
    public enum PublicationState {
        pending,
        queued,
        in_progress,
        succeeded,
        failed_retryable,
        failed_permanent,
        cancelled,
        waiting_for_channel
    }

    @Schema(description = "Workspace billing status")
    public enum BillingStatus {
        trial,
        active,
        grace,
        frozen
    }

    @Schema(description = "Media asset kind")
    public enum MediaKind {
        image,
        video,
        doc,
        audio
    }

    @Schema(description = "Media processing status")
    public enum MediaStatus {
        uploading,
        processing,
        ready,
        failed
    }

    @Schema(description = "OAuth identity provider")
    public enum AuthProvider {
        vk,
        yandex,
        telegram
    }

    @Schema(description = "Billing period for checkout")
    public enum BillingPeriod {
        month,
        year
    }
}
