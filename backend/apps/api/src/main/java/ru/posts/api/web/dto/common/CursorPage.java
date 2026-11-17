package ru.posts.api.web.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/** Cursor-based pagination (opaque cursor, no offset). */
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

    @Schema(name = "CursorPage", description = "Cursor page envelope")
    public record Response<T>(
            @Schema(description = "Page items") List<T> items,
            @Schema(description = "Cursor for the next page; null when no more items", nullable = true)
                    String nextCursor,
            @Schema(description = "Whether more items exist after this page") boolean hasMore) {}
}
