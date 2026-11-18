package ru.posts.api.web.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import ru.posts.api.web.dto.admin.AdminDtos;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.billing.BillingDtos;
import ru.posts.api.web.dto.channel.ChannelDtos;
import ru.posts.api.web.dto.ingest.IngestDtos;
import ru.posts.api.web.dto.media.MediaDtos;
import ru.posts.api.web.dto.notification.NotificationDtos;
import ru.posts.api.web.dto.ord.OrdDtos;
import ru.posts.api.web.dto.post.PostDtos;
import ru.posts.api.web.dto.project.ProjectDtos;
import ru.posts.api.web.dto.workspace.WorkspaceDtos;

/**
 * Cursor pagination. Concrete page records (not {@code Response<T>}) so springdoc emits the correct
 * item schema for each list operation (AN-4 blocker #1).
 */
public final class CursorPage {

    private CursorPage() {}

    @Schema(name = "CursorPageParams", description = "Query parameters for cursor pagination")
    public record Params(
            @Schema(
                            description = "Opaque cursor from a previous response `nextCursor`",
                            example = "eyJpZCI6IjAxOWEwMDAwLTAwMDAtNzAwMC04MDAwLTAwMDAwMDAwMDAwMSJ9")
                    String cursor,
            @Min(1) @Max(100) @Schema(description = "Page size", defaultValue = "20", example = "20") Integer limit) {

        public int limitOrDefault() {
            return limit == null ? 20 : limit;
        }
    }

    @Schema(name = "WorkspacePage")
    public record WorkspacePage(List<WorkspaceDtos.Workspace> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "WorkspaceMemberPage")
    public record WorkspaceMemberPage(List<WorkspaceDtos.WorkspaceMember> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "InvitationPage")
    public record InvitationPage(List<WorkspaceDtos.Invitation> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "ProjectPage")
    public record ProjectPage(List<ProjectDtos.Project> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "ChannelPage")
    public record ChannelPage(List<ChannelDtos.Channel> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "ChannelSetPage")
    public record ChannelSetPage(List<ChannelDtos.ChannelSet> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "PostPage")
    public record PostPage(List<PostDtos.Post> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "PublicationPage")
    public record PublicationPage(List<PostDtos.Publication> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "MediaPage")
    public record MediaPage(List<MediaDtos.MediaAsset> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "NotificationPage")
    public record NotificationPage(List<NotificationDtos.Notification> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "SessionPage")
    public record SessionPage(List<AuthDtos.SessionDto> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "InvoicePage")
    public record InvoicePage(List<BillingDtos.Invoice> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "AuditLogPage")
    public record AuditLogPage(List<WorkspaceDtos.AuditLogEntry> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "OrdConnectionPage")
    public record OrdConnectionPage(List<OrdDtos.OrdConnection> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "OrdContractorPage")
    public record OrdContractorPage(List<OrdDtos.OrdContractor> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "OrdContractPage")
    public record OrdContractPage(List<OrdDtos.OrdContract> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "GrabberRulePage")
    public record GrabberRulePage(List<IngestDtos.GrabberRule> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "GrabberRunPage")
    public record GrabberRunPage(List<IngestDtos.GrabberRun> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "GrabberItemPage")
    public record GrabberItemPage(List<IngestDtos.GrabberItem> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "AdminUserPage")
    public record AdminUserPage(List<AdminDtos.UserSummary> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "AdminWorkspacePage")
    public record AdminWorkspacePage(List<AdminDtos.WorkspaceSummary> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "AdminPromoPage")
    public record AdminPromoPage(List<AdminDtos.PromoCode> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "AdminQueueMessagePage")
    public record AdminQueueMessagePage(List<AdminDtos.QueueMessage> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "AdminAuditPage")
    public record AdminAuditPage(List<AdminDtos.AuditEntry> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "AdminAnnouncementPage")
    public record AdminAnnouncementPage(List<AdminDtos.Announcement> items, String nextCursor, boolean hasMore) {}
}
