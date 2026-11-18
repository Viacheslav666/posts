package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.analytics.AnalyticsService;
import ru.posts.api.web.dto.analytics.AnalyticsDtos;
import ru.posts.security.auth.SecurityAuthSupport;

/**
 * BE-30: post analytics parity with vposter {@code /posts/stats}. Reads materialised monthly
 * aggregates only (no hot-partition scans).
 */
@RestController
@Validated
@RequestMapping(path = "/api/v1/analytics", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Analytics", description = "Workspace publication analytics (materialised monthly aggregates)")
@OpenApiResponses
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/posts")
    @Operation(
            operationId = "analyticsPostsGet",
            summary = "Monthly post analytics for a workspace/year",
            description =
                    """
                    Returns 12 months of counts (regular / ad / repost), per-network succeeded/failed
                    publications, top errors (with retry link), and ORD ad revenue (with VAT, without VAT,
                    VAT, 3% fee). Data comes from `analytics_posts_monthly` (Europe/Moscow buckets).
                    Requires `analytics.view`.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Monthly analytics",
            content = @Content(schema = @Schema(implementation = AnalyticsDtos.PostsAnalyticsResponse.class)))
    public AnalyticsDtos.PostsAnalyticsResponse getPostsAnalytics(
            @RequestParam @NotNull UUID workspaceId, @RequestParam @NotNull @Min(2000) @Max(2100) Integer year) {
        return analyticsService.getPostsAnalytics(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, year);
    }
}
