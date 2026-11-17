package ru.posts.api.web;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import ru.posts.api.web.dto.common.ProblemDetailDto;

/** Common documented responses for stub and future real endpoints. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponses({
    @ApiResponse(
            responseCode = "501",
            description = "Stub — not implemented yet (BE-3)",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetailDto.class))),
    @ApiResponse(
            responseCode = "400",
            description = "Validation failed (`validation_failed`)",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetailDto.class))),
    @ApiResponse(
            responseCode = "401",
            description = "Unauthorized (`unauthorized`)",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetailDto.class))),
    @ApiResponse(
            responseCode = "403",
            description = "Forbidden / insufficient permissions (`forbidden`, `insufficient_permissions`)",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetailDto.class))),
    @ApiResponse(
            responseCode = "404",
            description = "Not found or IDOR (`not_found`)",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetailDto.class))),
    @ApiResponse(
            responseCode = "409",
            description =
                    "Conflict (`conflict`, `optimistic_lock_conflict`, `trial_already_used`, `idempotency_conflict`)",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetailDto.class))),
    @ApiResponse(
            responseCode = "429",
            description = "Rate limited (`rate_limited`)",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetailDto.class)))
})
public @interface OpenApiResponses {}
