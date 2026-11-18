package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.auth.ClientRequestContexts;
import ru.posts.api.post.PostService;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.post.PostDtos;
import ru.posts.security.auth.SecurityAuthSupport;

@RestController
@Validated
@RequestMapping(path = "/api/v1/posts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Posts", description = "Posts, variants, schedule actions, preview, bulk")
@OpenApiResponses
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    @Operation(operationId = "postsList", summary = "List posts with filters and cursor pagination")
    @ApiResponse(responseCode = "200", description = "Posts page")
    public CursorPage.PostPage list(
            @RequestParam UUID workspaceId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) ApiEnums.PostStatus status,
            @RequestParam(required = false) UUID channelId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) ApiEnums.Network network,
            @RequestParam(required = false) UUID authorId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PostDtos.PostSort sort,
            @Valid @ParameterObject CursorPage.Params page) {
        return postService.list(
                SecurityAuthSupport.requirePrincipal().userId(),
                workspaceId,
                from,
                to,
                status,
                channelId,
                projectId,
                network,
                authorId,
                q,
                sort,
                page);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "postsCreate", summary = "Create a draft or scheduled post")
    @ApiResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(schema = @Schema(implementation = PostDtos.Post.class)))
    public PostDtos.Post create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PostDtos.CreatePostRequest request,
            HttpServletRequest httpRequest) {
        return postService.create(
                SecurityAuthSupport.requirePrincipal().userId(),
                request,
                idempotencyKey,
                ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/{postId}")
    @Operation(operationId = "postsGet", summary = "Get post with variants")
    @ApiResponse(
            responseCode = "200",
            description = "Post",
            content = @Content(schema = @Schema(implementation = PostDtos.Post.class)))
    public PostDtos.Post get(@PathVariable UUID postId) {
        return postService.get(SecurityAuthSupport.requirePrincipal().userId(), postId);
    }

    @PutMapping("/{postId}")
    @Operation(operationId = "postsUpdate", summary = "Update post (optimistic locking via version)")
    @ApiResponse(responseCode = "200", description = "Updated")
    public PostDtos.Post update(
            @PathVariable UUID postId,
            @Valid @RequestBody PostDtos.UpdatePostRequest request,
            HttpServletRequest httpRequest) {
        return postService.update(
                SecurityAuthSupport.requirePrincipal().userId(),
                postId,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @DeleteMapping("/{postId}")
    @Operation(operationId = "postsDelete", summary = "Soft-delete a post and cancel pending publications")
    @ApiResponse(responseCode = "200", description = "Deleted")
    public AuthDtos.MessageResponse delete(@PathVariable UUID postId, HttpServletRequest httpRequest) {
        return postService.delete(
                SecurityAuthSupport.requirePrincipal().userId(), postId, ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/{postId}/schedule")
    @Operation(operationId = "postsSchedule", summary = "Schedule or reschedule a post")
    @ApiResponse(responseCode = "200", description = "Scheduled")
    public PostDtos.Post schedule(
            @PathVariable UUID postId,
            @Valid @RequestBody PostDtos.SchedulePostRequest request,
            HttpServletRequest httpRequest) {
        return postService.schedule(
                SecurityAuthSupport.requirePrincipal().userId(),
                postId,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/{postId}/pause")
    @Operation(operationId = "postsPause", summary = "Pause scheduled publications for a post")
    @ApiResponse(responseCode = "200", description = "Paused")
    public PostDtos.Post pause(
            @PathVariable UUID postId,
            @Valid @RequestBody PostDtos.PostActionRequest request,
            HttpServletRequest httpRequest) {
        return postService.pause(
                SecurityAuthSupport.requirePrincipal().userId(),
                postId,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/{postId}/resume")
    @Operation(operationId = "postsResume", summary = "Resume a paused post")
    @ApiResponse(responseCode = "200", description = "Resumed")
    public PostDtos.Post resume(
            @PathVariable UUID postId,
            @Valid @RequestBody PostDtos.PostActionRequest request,
            HttpServletRequest httpRequest) {
        return postService.resume(
                SecurityAuthSupport.requirePrincipal().userId(),
                postId,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/{postId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "postsDuplicate", summary = "Duplicate a post as a new draft")
    @ApiResponse(responseCode = "201", description = "Duplicated draft")
    public PostDtos.Post duplicate(@PathVariable UUID postId, HttpServletRequest httpRequest) {
        return postService.duplicate(
                SecurityAuthSupport.requirePrincipal().userId(), postId, ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/bulk")
    @Operation(operationId = "postsBulk", summary = "Bulk pause/resume/delete/reschedule/duplicate (US-4.7, FE-8)")
    @ApiResponse(
            responseCode = "200",
            description = "Bulk result",
            content = @Content(schema = @Schema(implementation = PostDtos.BulkPostsResponse.class)))
    public PostDtos.BulkPostsResponse bulk(
            @Valid @RequestBody PostDtos.BulkPostsRequest request, HttpServletRequest httpRequest) {
        return postService.bulk(
                SecurityAuthSupport.requirePrincipal().userId(), request, ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/preview")
    @Operation(operationId = "postsPreview", summary = "Server-side render/validate a variant for a network")
    @ApiResponse(
            responseCode = "200",
            description = "Preview",
            content = @Content(schema = @Schema(implementation = PostDtos.PreviewResponse.class)))
    public PostDtos.PreviewResponse preview(@Valid @RequestBody PostDtos.PreviewRequest request) {
        return postService.preview(SecurityAuthSupport.requirePrincipal().userId(), request);
    }

    @GetMapping("/{postId}/publications")
    @Operation(operationId = "postsListPublications", summary = "List publications for a post")
    @ApiResponse(responseCode = "200", description = "Publications page")
    public CursorPage.PublicationPage listPublications(
            @PathVariable UUID postId, @Valid @ParameterObject CursorPage.Params page) {
        return postService.listPublications(
                SecurityAuthSupport.requirePrincipal().userId(), postId, page);
    }
}
