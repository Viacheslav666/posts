package ru.posts.api.post;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.post.PostDtos;

public interface PostService {

    CursorPage.PostPage list(
            UUID userId,
            UUID workspaceId,
            Instant from,
            Instant to,
            ApiEnums.PostStatus status,
            UUID channelId,
            UUID projectId,
            ApiEnums.Network network,
            UUID authorId,
            String q,
            PostDtos.PostSort sort,
            PostDtos.ContentKind contentKind,
            CursorPage.Params page);

    PostDtos.Post get(UUID userId, UUID postId);

    PostDtos.Post create(
            UUID userId, PostDtos.CreatePostRequest request, String idempotencyKey, ClientRequestContext ctx);

    PostDtos.Post update(UUID userId, UUID postId, PostDtos.UpdatePostRequest request, ClientRequestContext ctx);

    AuthDtos.MessageResponse delete(UUID userId, UUID postId, ClientRequestContext ctx);

    PostDtos.Post schedule(UUID userId, UUID postId, PostDtos.SchedulePostRequest request, ClientRequestContext ctx);

    PostDtos.Post pause(UUID userId, UUID postId, PostDtos.PostActionRequest request, ClientRequestContext ctx);

    PostDtos.Post resume(UUID userId, UUID postId, PostDtos.PostActionRequest request, ClientRequestContext ctx);

    PostDtos.Post duplicate(UUID userId, UUID postId, ClientRequestContext ctx);

    PostDtos.BulkPostsResponse bulk(UUID userId, PostDtos.BulkPostsRequest request, ClientRequestContext ctx);

    PostDtos.PreviewResponse preview(UUID userId, PostDtos.PreviewRequest request);

    PostDtos.BatchPostsResponse batchPosts(
            UUID userId, PostDtos.BatchPostsRequest request, String idempotencyKey, ClientRequestContext ctx);

    CursorPage.PublicationPage listPublications(UUID userId, UUID postId, CursorPage.Params page);

    List<PostDtos.CalendarItem> calendar(
            UUID userId,
            UUID workspaceId,
            Instant from,
            Instant to,
            UUID projectId,
            UUID channelId,
            UUID authorId,
            ApiEnums.Network network,
            ApiEnums.PostStatus status,
            PostDtos.ContentKind contentKind,
            String timezone);

    PostDtos.Post moveCalendar(
            UUID userId, UUID postId, PostDtos.MoveCalendarItemRequest request, ClientRequestContext ctx);

    PostDtos.BatchStoriesResponse batchStories(
            UUID userId, PostDtos.BatchStoriesRequest request, String idempotencyKey, ClientRequestContext ctx);
}
