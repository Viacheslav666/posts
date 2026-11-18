package ru.posts.api.web.dto.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** BE-30 response DTOs — parity with vposter `/posts/stats`. */
public final class AnalyticsDtos {

    private AnalyticsDtos() {}

    @Schema(name = "PostsAnalyticsResponse", description = "Monthly post analytics for a workspace/year")
    public record PostsAnalyticsResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID workspaceId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026") int year,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Europe/Moscow") String timezone,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Always 12 months (1..12)")
                    List<PostsAnalyticsMonth> months,
            Instant computedAt) {}

    @Schema(name = "PostsAnalyticsMonth")
    public record PostsAnalyticsMonth(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "12") int month,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Non-ad, non-repost publications")
                    long regularCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Publications of is_ad posts")
                    long adCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Publications with base_content.repost")
                    long repostCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<NetworkPublicationStats> networks,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TopPublicationError> topErrors,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AdRevenue revenue,
            Instant computedAt) {}

    @Schema(name = "NetworkPublicationStats")
    public record NetworkPublicationStats(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "vk") String network,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long succeeded,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long failed) {}

    @Schema(name = "TopPublicationError")
    public record TopPublicationError(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String errorCode,
            String errorMessage,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long count,
            UUID publicationId,
            UUID postId,
            Instant runAt,
            String network,
            @Schema(description = "API path to retry the sample publication") String retryPath) {}

    @Schema(name = "AdRevenue", description = "ORD invoice totals for the month (amounts include VAT when taxed)")
    public record AdRevenue(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "120.00") BigDecimal amountWithVat,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "100.00") BigDecimal amountWithoutVat,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "20.00") BigDecimal vatAmount,
            @Schema(
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            description = "3% advertising levy on amountWithVat (HALF_UP, scale 2)",
                            example = "3.60")
                    BigDecimal fee3Pct) {}
}
