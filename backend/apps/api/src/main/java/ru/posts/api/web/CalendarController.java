package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.auth.ClientRequestContexts;
import ru.posts.api.post.PostService;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.post.PostDtos;
import ru.posts.security.auth.SecurityAuthSupport;

@RestController
@RequestMapping(path = "/api/v1/calendar", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Calendar", description = "Lightweight content calendar queries and drag-and-drop moves")
@OpenApiResponses
public class CalendarController {

    private final PostService postService;

    public CalendarController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    @Operation(
            operationId = "calendarList",
            summary = "List calendar items in a time range",
            description = "Returns lightweight DTOs for month/week/list views (US-4.6). "
                    + "`from`/`to` are UTC instants; optional `timezone` (IANA) documents the "
                    + "client calendar day boundary used to compute the range.")
    @ApiResponse(
            responseCode = "200",
            description = "Calendar items",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PostDtos.CalendarItem.class))))
    public List<PostDtos.CalendarItem> list(
            @RequestParam UUID workspaceId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID channelId,
            @RequestParam(required = false) UUID authorId,
            @RequestParam(required = false) ApiEnums.Network network,
            @RequestParam(required = false) ApiEnums.PostStatus status,
            @RequestParam(required = false) @Schema(example = "Europe/Moscow") String timezone) {
        return postService.calendar(
                SecurityAuthSupport.requirePrincipal().userId(),
                workspaceId,
                from,
                to,
                projectId,
                channelId,
                authorId,
                network,
                status,
                timezone);
    }

    @PostMapping("/{postId}/move")
    @Operation(operationId = "calendarMove", summary = "Move a scheduled post via drag-and-drop")
    @ApiResponse(
            responseCode = "200",
            description = "Moved",
            content = @Content(schema = @Schema(implementation = PostDtos.Post.class)))
    public PostDtos.Post move(
            @PathVariable UUID postId,
            @Valid @RequestBody PostDtos.MoveCalendarItemRequest request,
            HttpServletRequest httpRequest) {
        return postService.moveCalendar(
                SecurityAuthSupport.requirePrincipal().userId(),
                postId,
                request,
                ClientRequestContexts.from(httpRequest));
    }
}
