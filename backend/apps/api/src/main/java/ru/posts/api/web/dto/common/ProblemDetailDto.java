package ru.posts.api.web.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.List;
import java.util.Map;

/** Documented shape of Spring ProblemDetail responses (RFC 7807 + Posts `code`). */
@Schema(name = "ProblemDetail", description = "RFC 7807 problem with Posts machine-readable code")
public record ProblemDetailDto(
        @Schema(example = "https://posts.ru/problems/not_implemented") URI type,
        @Schema(example = "Not Implemented") String title,
        @Schema(example = "501") int status,
        @Schema(example = "Operation not implemented yet: workspaces.list") String detail,
        @Schema(example = "/api/v1/workspaces") String instance,
        @Schema(description = "Stable machine-readable error code", example = "not_implemented") String code,
        @Schema(description = "Field-level validation errors when code=validation_failed")
                List<Map<String, String>> errors) {}
