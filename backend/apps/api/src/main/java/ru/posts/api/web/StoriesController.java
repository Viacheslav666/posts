package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.auth.ClientRequestContexts;
import ru.posts.api.post.PostService;
import ru.posts.api.web.dto.post.PostDtos;
import ru.posts.security.auth.SecurityAuthSupport;

@RestController
@Validated
@RequestMapping(path = "/api/v1/stories", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Stories", description = "Batch scheduled stories (BE-16 / E11)")
@OpenApiResponses
public class StoriesController {

    private final PostService postService;

    public StoriesController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "storiesBatch",
            summary = "Batch-create scheduled stories",
            description = "Creates up to 50 story posts (best-effort). Requires posts.bulk + posts.schedule.")
    @ApiResponse(
            responseCode = "201",
            description = "Batch result",
            content = @Content(schema = @Schema(implementation = PostDtos.BatchStoriesResponse.class)))
    public PostDtos.BatchStoriesResponse batch(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PostDtos.BatchStoriesRequest request,
            HttpServletRequest httpRequest) {
        return postService.batchStories(
                SecurityAuthSupport.requirePrincipal().userId(),
                request,
                idempotencyKey,
                ClientRequestContexts.from(httpRequest));
    }
}
