package ru.posts.api.config;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.error.NotImplementedException;
import ru.posts.api.post.PostService;
import ru.posts.api.post.PublicationService;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.post.PostDtos;

/** Stubs so openapi profile can boot Post/Calendar/Publication controllers. */
@Configuration
@Profile("openapi")
public class OpenApiPostStubConfig {

    @Bean
    PostService postService() {
        return new PostService() {
            @Override
            public CursorPage.PostPage list(
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
                    CursorPage.Params page) {
                return new CursorPage.PostPage(List.of(), null, false);
            }

            @Override
            public PostDtos.Post get(UUID userId, UUID postId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public PostDtos.Post create(
                    UUID userId, PostDtos.CreatePostRequest request, String idempotencyKey, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public PostDtos.Post update(
                    UUID userId, UUID postId, PostDtos.UpdatePostRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse delete(UUID userId, UUID postId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public PostDtos.Post schedule(
                    UUID userId, UUID postId, PostDtos.SchedulePostRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public PostDtos.Post pause(
                    UUID userId, UUID postId, PostDtos.PostActionRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public PostDtos.Post resume(
                    UUID userId, UUID postId, PostDtos.PostActionRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public PostDtos.Post duplicate(UUID userId, UUID postId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public PostDtos.BulkPostsResponse bulk(
                    UUID userId, PostDtos.BulkPostsRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public PostDtos.PreviewResponse preview(UUID userId, PostDtos.PreviewRequest request) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public CursorPage.PublicationPage listPublications(UUID userId, UUID postId, CursorPage.Params page) {
                return new CursorPage.PublicationPage(List.of(), null, false);
            }

            @Override
            public List<PostDtos.CalendarItem> calendar(
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
                    String timezone) {
                return List.of();
            }

            @Override
            public PostDtos.Post moveCalendar(
                    UUID userId, UUID postId, PostDtos.MoveCalendarItemRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public PostDtos.BatchStoriesResponse batchStories(
                    UUID userId,
                    PostDtos.BatchStoriesRequest request,
                    String idempotencyKey,
                    ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }
        };
    }

    @Bean
    PublicationService publicationService() {
        return new PublicationService() {
            @Override
            public CursorPage.PublicationPage list(
                    UUID userId,
                    UUID workspaceId,
                    UUID postId,
                    UUID channelId,
                    ApiEnums.PublicationState state,
                    ApiEnums.Network network,
                    CursorPage.Params page) {
                return new CursorPage.PublicationPage(List.of(), null, false);
            }

            @Override
            public PostDtos.Publication get(UUID userId, UUID publicationId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public PostDtos.Publication retry(UUID userId, UUID publicationId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public List<PostDtos.PublicationAttempt> listAttempts(UUID userId, UUID publicationId) {
                return List.of();
            }
        };
    }
}
